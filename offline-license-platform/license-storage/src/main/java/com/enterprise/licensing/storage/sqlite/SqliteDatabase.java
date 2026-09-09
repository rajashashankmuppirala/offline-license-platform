package com.enterprise.licensing.storage.sqlite;

import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

public final class SqliteDatabase implements AutoCloseable {
    private final SQLiteDataSource dataSource;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public SqliteDatabase(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().normalize().getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
        } catch (java.io.IOException e) {
            throw new SqliteStorageException("Unable to create SQLite data directory", e);
        }
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        migrate();
    }

    public DataSource dataSource() { return dataSource; }

    public boolean transactionActive() { return transactionConnection.get() != null; }

    public void migrate() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                return null;
            } catch (SQLException e) { throw new SqliteStorageException("Unable to configure SQLite", e); }
        });
    }

    public <T> T withConnection(Function<Connection, T> work) {
        Connection current = transactionConnection.get();
        if (current != null) return work.apply(current);
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
            }
            return work.apply(connection);
        } catch (SQLException e) { throw new SqliteStorageException("SQLite operation failed", e); }
    }

    Connection bind(Connection connection) { transactionConnection.set(connection); return connection; }
    void unbind() { transactionConnection.remove(); }
    @Override public void close() { }
}
