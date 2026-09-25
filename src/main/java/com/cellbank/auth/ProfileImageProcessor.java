package com.cellbank.auth;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProfileImageProcessor {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;
    private static final int MAX_DIMENSION = 4096;
    private static final int OUTPUT_DIMENSION = 256;

    public byte[] process(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw badRequest("Choose an image to upload.");
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Profile picture must not exceed 2 MB."
            );
        }

        BufferedImage source = readImage(file);

        try {
            return resizeAndEncode(source);
        } finally {
            source.flush();
        }
    }

    private BufferedImage readImage(MultipartFile file) {

        try (InputStream input = file.getInputStream();
             MemoryCacheImageInputStream imageInput =
                     new MemoryCacheImageInputStream(input)) {

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(imageInput);

            if (!readers.hasNext()) {
                throw badRequest("Upload a valid JPEG or PNG image.");
            }

            ImageReader reader = readers.next();

            try {
                String format = reader.getFormatName();

                if (!format.equalsIgnoreCase("JPEG")
                        && !format.equalsIgnoreCase("PNG")) {

                    throw badRequest("Only JPEG and PNG images are supported.");
                }

                reader.setInput(imageInput, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0
                        || width > MAX_DIMENSION
                        || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {

                    throw badRequest(
                            "Image must not exceed 4096 pixels per side "
                                    + "or 12 million pixels in total."
                    );
                }

                BufferedImage image = reader.read(0);

                if (image == null) {
                    throw badRequest("The image could not be read.");
                }

                return image;
            } finally {
                reader.dispose();
            }

        } catch (IOException exception) {
            throw badRequest(
                    "The image could not be read. Choose another JPEG or PNG."
            );
        }
    }

    private byte[] resizeAndEncode(BufferedImage source) {

        double scale = Math.min(
                1.0,
                (double) OUTPUT_DIMENSION
                        / Math.max(source.getWidth(), source.getHeight())
        );

        int width = Math.max(
                1,
                (int) Math.round(source.getWidth() * scale)
        );

        int height = Math.max(
                1,
                (int) Math.round(source.getHeight() * scale)
        );

        BufferedImage resized = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        try {
            Graphics2D graphics = resized.createGraphics();

            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC
                );

                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                if (!ImageIO.write(resized, "png", output)) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Image processing is unavailable."
                    );
                }

                return output.toByteArray();
            } catch (IOException exception) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not prepare the profile picture."
                );
            }
        } finally {
            resized.flush();
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
