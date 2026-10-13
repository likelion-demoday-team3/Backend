package com.example.kiwi_backend.live.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
final class LiveAfterCommit {
    private LiveAfterCommit() { }

    static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An active transaction is required");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    // The database is already committed; this is not a rollback or retry.
                    log.error("Live post-commit action failed", e);
                }
            }
        });
    }
}
