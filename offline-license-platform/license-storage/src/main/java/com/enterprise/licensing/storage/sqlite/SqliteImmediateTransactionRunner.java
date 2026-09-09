package com.enterprise.licensing.storage.sqlite;

import com.enterprise.licensing.engine.ports.TransactionRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class SqliteImmediateTransactionRunner implements TransactionRunner {
    private final SqliteDatabase database;
    private final ReentrantLock writer = new ReentrantLock(true);
    private final int attempts;

    public SqliteImmediateTransactionRunner(SqliteDatabase database) {
        this(database, 5);
    }

    public SqliteImmediateTransactionRunner(SqliteDatabase database, int attempts) {
        this.database = database;
        this.attempts = attempts;
    }

    private static boolean isBusy(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().toLowerCase(java.util.Locale.ROOT).contains("busy"))
                return true;
        }
        return false;
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return database.withConnection(ignored -> work.get());
    }

    @Override
    public <T> T write(Supplier<T> work) {
        if (database.transactionActive()) return work.get();
        writer.lock();
        try {
            RuntimeException last = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    return execute(work);
                } catch (SqliteStorageException e) {
                    last = e;
                    if (!isBusy(e) || attempt == attempts) throw e;
                    try {
                        Thread.sleep(10L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
            throw last;
        } finally {
            writer.unlock();
        }
    }

    public <T> T exclusive(Supplier<T> work) {
        writer.lock();
        try {
            return work.get();
        } finally {
            writer.unlock();
        }
    }

    private <T> T execute(Supplier<T> work) {
        try (Connection connection = database.dataSource().getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("BEGIN IMMEDIATE");
            }
            database.bind(connection);
            try {
                T result = work.get();
                try (Statement statement = connection.createStatement()) {
                    statement.execute("COMMIT");
                }
                return result;
            } catch (RuntimeException e) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ROLLBACK");
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                database.unbind();
            }
        } catch (SQLException e) {
            throw new SqliteStorageException("SQLite transaction failed", e);
        }
    }
}
