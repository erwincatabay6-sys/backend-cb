package com.cellbank.ai;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/repairs/{repairId}/ai")
public class AiTroubleshootingController {

    private final AiMessageReadService readService;
    private final AiTroubleshootingContextService contextService;
    private final AiTroubleshootingService troubleshootingService;

    public AiTroubleshootingController(
            AiMessageReadService readService,
            AiTroubleshootingContextService contextService,
            AiTroubleshootingService troubleshootingService) {

        this.readService = readService;
        this.contextService = contextService;
        this.troubleshootingService = troubleshootingService;
    }

    @GetMapping("/messages")
    public ResponseEntity<List<AiMessageResponse>> getMessages(
            Principal principal,
            @PathVariable("repairId") Long repairId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(readService.getMessages(
                        principal.getName(),
                        repairId));
    }

    @GetMapping("/context")
    public ResponseEntity<AiTroubleshootingContext> getContext(
            Principal principal,
            @PathVariable("repairId") Long repairId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(contextService.buildContext(
                        principal.getName(),
                        repairId));
    }

    @PostMapping(
            value = "/messages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AiMessageResponse>> sendMessage(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @Valid @RequestPart("request")
            AiMessageCreateRequest request,
            @RequestPart(value = "attachments", required = false)
            List<MultipartFile> attachments) {

        List<AiMessageResponse> messages =
                troubleshootingService.sendMessage(
                        principal.getName(),
                        repairId,
                        request,
                        attachments);

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(messages);
    }

    @GetMapping("/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> getAttachment(
            Principal principal,
            @PathVariable("repairId") Long repairId,
            @PathVariable("messageId") Long messageId,
            @PathVariable("attachmentId") Long attachmentId) {

        AiMessageAttachment attachment = readService.getAttachment(
                principal.getName(),
                repairId,
                messageId,
                attachmentId);

        byte[] imageData = attachment.getImageData();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        attachment.getContentType()))
                .contentLength(imageData.length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(imageData);
    }
}
