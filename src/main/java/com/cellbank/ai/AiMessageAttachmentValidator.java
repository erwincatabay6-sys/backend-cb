package com.cellbank.ai;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiMessageAttachmentValidator {

    private static final int MAX_ATTACHMENTS = 3;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final int MAX_FILENAME_LENGTH = 255;

    public List<ValidatedAttachment> validate(
            List<MultipartFile> attachments) {

        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        if (attachments.size() > MAX_ATTACHMENTS) {
            throw badRequest("You can attach up to 3 images per message.");
        }

        List<ValidatedAttachment> validated =
                new ArrayList<>(attachments.size());

        for (int index = 0; index < attachments.size(); index++) {
            MultipartFile attachment = attachments.get(index);

            if (attachment == null || attachment.isEmpty()) {
                throw badRequest(
                        "An attached image is empty. Remove it and try again.");
            }

            if (attachment.getSize() > MAX_IMAGE_BYTES) {
                throw imageTooLarge();
            }

            byte[] imageData = readBytes(attachment);
            String contentType = validateImage(imageData);

            String filename = sanitizeFilename(
                    attachment.getOriginalFilename(),
                    contentType,
                    index + 1);

            validated.add(new ValidatedAttachment(
                    filename,
                    contentType,
                    imageData,
                    (short) (index + 1)));
        }

        return List.copyOf(validated);
    }

    private byte[] readBytes(MultipartFile attachment) {
        try (InputStream input = attachment.getInputStream()) {
            // Bound the read even if the reported upload size is incorrect.
            byte[] imageData = input.readNBytes(MAX_IMAGE_BYTES + 1);

            if (imageData.length > MAX_IMAGE_BYTES) {
                throw imageTooLarge();
            }

            if (imageData.length == 0) {
                throw badRequest("An attached image is empty.");
            }

            return imageData;
        } catch (IOException exception) {
            throw badRequest(
                    "An attached image could not be read. Attach it again.");
        }
    }

    private String validateImage(byte[] imageData) {
        try (MemoryCacheImageInputStream input =
                new MemoryCacheImageInputStream(
                        new java.io.ByteArrayInputStream(imageData))) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw badRequest(
                        "Attach a valid JPEG, PNG, or WebP image.");
            }

            ImageReader reader = readers.next();

            try {
                String contentType = detectContentType(
                        reader.getFormatName());

                reader.setInput(input, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    throw badRequest(
                            "An attached image has invalid dimensions.");
                }

                if (width > MAX_DIMENSION
                        || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {

                    throw badRequest(
                            "An attached image is too large in dimensions. "
                            + "Resize it to no more than 20 megapixels and "
                            + "8,192 pixels on either side.");
                }

                // Decode the image to check that its contents are readable.
                BufferedImage decoded = reader.read(0);

                if (decoded == null) {
                    throw badRequest(
                            "An attached image could not be decoded.");
                }

                try {
                    if (decoded.getWidth() != width
                            || decoded.getHeight() != height) {
                        throw badRequest(
                                "An attached image has inconsistent dimensions.");
                    }
                } finally {
                    decoded.flush();
                }

                return contentType;
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw badRequest(
                    "An attached image is damaged or unsupported. "
                    + "Try exporting it as JPEG, PNG, or WebP.");
        }
    }

    private String detectContentType(String formatName) {
        return switch (formatName.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw badRequest(
                    "Only JPEG, PNG, and WebP images are supported.");
        };
    }

    private String sanitizeFilename(
            String originalFilename,
            String contentType,
            int position) {

        String filename = originalFilename == null
                ? ""
                : originalFilename.replace('\\', '/');

        int lastSlash = filename.lastIndexOf('/');

        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }

        filename = filename
                .replaceAll("\\p{Cntrl}", "")
                .strip();

        if (filename.isBlank()
                || filename.equals(".")
                || filename.equals("..")) {

            String extension = switch (contentType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> throw new IllegalArgumentException(
                        "Unsupported validated image type.");
            };

            filename = "image-" + position + extension;
        }

        if (filename.length() > MAX_FILENAME_LENGTH) {
            filename = filename.substring(0, MAX_FILENAME_LENGTH);

            // Avoid ending with half of a Unicode surrogate pair.
            if (Character.isHighSurrogate(
                    filename.charAt(filename.length() - 1))) {
                filename = filename.substring(0, filename.length() - 1);
            }
        }

        return filename;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message);
    }

    private ResponseStatusException imageTooLarge() {
        return new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE,
                "Each attached image must be 10 MB or smaller.");
    }

    public record ValidatedAttachment(
            String originalFilename,
            String contentType,
            byte[] imageData,
            short displayOrder) {

        public ValidatedAttachment {
            imageData = imageData.clone();
        }

        @Override
        public byte[] imageData() {
            return imageData.clone();
        }

        public long fileSize() {
            return imageData.length;
        }

        @Override
        public String toString() {
            return "ValidatedAttachment[contentType=" + contentType
                    + ", fileSize=" + fileSize()
                    + ", displayOrder=" + displayOrder
                    + "]";
        }
    }
}
