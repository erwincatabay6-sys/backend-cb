package com.cellbank.backup;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.cellbank.auth.CurrentUserResponse;
import com.cellbank.auth.CurrentUserService;
import com.cellbank.backup.BackupQueryService.DownloadInfo;
import com.cellbank.backup.BackupStatusResponse.BackupPage;

@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final CurrentUserService currentUserService;
    private final BackupQueryService queries;
    private final BackupExecutionService execution;
    private final BackupStorage storage;

    public BackupController(
            CurrentUserService currentUserService,
            BackupQueryService queries,
            BackupExecutionService execution,
            BackupStorage storage) {

        this.currentUserService = currentUserService;
        this.queries = queries;
        this.execution = execution;
        this.storage = storage;
    }

    @GetMapping("/status")
    public ResponseEntity<BackupStatusResponse> getStatus(
            Principal principal) {

        requireAdmin(principal);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queries.getStatus());
    }

    @GetMapping
    public ResponseEntity<BackupPage> getBackups(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page) {

        requireAdmin(principal);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queries.getAvailableBackups(page));
    }

    @PostMapping
    public ResponseEntity<BackupAcceptedResponse> createBackup(
            Principal principal) {

        CurrentUserResponse user = requireAdmin(principal);
        Long backupId = execution.requestManualBackup(user.id());

        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(new BackupAcceptedResponse(
                        backupId,
                        "Backup requested."
                ));
    }

    @GetMapping("/{backupId}/download")
    public void download(
            Principal principal,
            @PathVariable("backupId") Long backupId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        requireAdmin(principal);
        DownloadInfo download = queries.getDownload(backupId);

        try (InputStream input = storage.open(download.objectKey())) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(
                    MediaType.APPLICATION_OCTET_STREAM_VALUE
            );
            response.setContentLengthLong(download.sizeBytes());

            response.setHeader(
                    HttpHeaders.CACHE_CONTROL,
                    "no-store"
            );

            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                            .filename(download.fileName())
                            .build()
                            .toString()
            );

            response.setHeader(
                    "X-Backup-SHA256",
                    download.sha256()
            );

            if (!"HEAD".equalsIgnoreCase(request.getMethod())) {
                input.transferTo(response.getOutputStream());
            }

        } catch (IOException exception) {
            if (!response.isCommitted()) {
                response.reset();
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The backup file could not be downloaded. "
                                + "Refresh the backup list and try again."
                );
            }

            // Do not append a JSON error to a partially downloaded archive.
            throw exception;
        }
    }

    private CurrentUserResponse requireAdmin(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in again."
            );
        }

        CurrentUserResponse user =
                currentUserService.getCurrentUser(principal.getName());

        if (!user.roles().contains("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access is required."
            );
        }

        return user;
    }

    public record BackupAcceptedResponse(
            Long backupId,
            String message) {
    }
}
