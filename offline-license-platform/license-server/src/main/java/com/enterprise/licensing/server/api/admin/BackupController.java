package com.enterprise.licensing.server.api.admin;

import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.storage.operations.SqliteBackupService;

import java.nio.file.Path;
import java.time.Instant;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/admin/storage")
public final class BackupController {
    private final SqliteBackupService backups;
    private final LicenseServerIdentityService identity;

    public BackupController(SqliteBackupService backups, LicenseServerIdentityService identity) {
        this.backups = backups;
        this.identity = identity;
    }

    @PostMapping("/backup")
    public ApiResponse<StorageResult> backup(@RequestBody PathInput input) {
        Path path = backups.backup(Path.of(input.path()));
        return ApiResponse.success(new StorageResult(path.toString(), LicenseReasonCode.LICENSE_VALID));
    }

    @PostMapping("/restore/validate")
    public ApiResponse<StorageResult> validate(@RequestBody PathInput input) {
        var reason = backups.validateForOriginalIdentity(Path.of(input.path()), identity.publicIdentity());
        return new ApiResponse<>(reason == LicenseReasonCode.LICENSE_VALID, reason, Instant.now(), new StorageResult(input.path(), reason));
    }

    public record PathInput(String path) {
    }

    public record StorageResult(String path, LicenseReasonCode reasonCode) {
    }
}
