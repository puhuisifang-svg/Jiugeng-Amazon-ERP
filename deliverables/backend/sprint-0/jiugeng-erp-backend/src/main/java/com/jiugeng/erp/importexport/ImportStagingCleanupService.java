package com.jiugeng.erp.importexport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class ImportStagingCleanupService {
    private final ImportRepository repository;
    private final LocalImportFileStore files;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    ImportStagingCleanupService(ImportRepository repository, LocalImportFileStore files,
            @Value("${app.imports.retention-days:7}") long retentionDays) {
        this(repository, files, Duration.ofDays(retentionDays), Clock.systemUTC());
    }

    ImportStagingCleanupService(ImportRepository repository, LocalImportFileStore files,
            Duration retention, Clock clock) {
        if (retention.isNegative() || retention.isZero()) throw new IllegalArgumentException("Import retention must be positive");
        this.repository = repository;
        this.files = files;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.imports.cleanup-cron:0 30 3 * * *}")
    void scheduledCleanup() {
        cleanup(Instant.now(clock));
    }

    LocalImportFileStore.CleanupResult cleanup(Instant now) {
        return files.cleanup(repository.activeStorageKeys(), now.minus(retention));
    }
}
