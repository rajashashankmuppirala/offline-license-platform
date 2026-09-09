package com.enterprise.licensing.storage.operations;

import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.sqlite.*;
import java.nio.file.*;
import java.sql.DriverManager;

public final class SqliteBackupService {
    private final SqliteDatabase database;
    private final SqliteImmediateTransactionRunner transactions;
    public SqliteBackupService(SqliteDatabase database,SqliteImmediateTransactionRunner transactions){this.database=database;this.transactions=transactions;}

    public Path backup(Path target){
        Path normalized=target.toAbsolutePath().normalize();
        return transactions.exclusive(()->{boolean existed=Files.exists(normalized);try{
            if(existed)throw new IllegalArgumentException("Backup target already exists");
            Path parent=normalized.getParent();if(parent!=null)Files.createDirectories(parent);
            database.withConnection(connection->{try(var statement=connection.prepareStatement("VACUUM INTO ?")){statement.setString(1,normalized.toString());statement.execute();return null;}catch(Exception exception){throw new SqliteStorageException("SQLite backup failed",exception);}});
            if(!integrityOk(normalized))throw new SqliteStorageException("Backup integrity check failed",null);
            return normalized;
        }catch(RuntimeException|java.io.IOException exception){if(!existed)try{Files.deleteIfExists(normalized);}catch(java.io.IOException cleanup){exception.addSuppressed(cleanup);}if(exception instanceof RuntimeException runtime)throw runtime;throw new SqliteStorageException("Backup file operation failed",exception);}});
    }

    public LicenseReasonCode validateForOriginalIdentity(Path backup,LicenseServerIdentity identity){
        if(!Files.isRegularFile(backup)||!integrityOk(backup))return LicenseReasonCode.STATE_INTEGRITY_FAILURE;
        try(var connection=DriverManager.getConnection("jdbc:sqlite:file:"+backup.toAbsolutePath()+"?mode=ro");var statement=connection.prepareStatement("SELECT server_id,public_key_fingerprint FROM license_server_identity WHERE singleton_id=1");var result=statement.executeQuery()){
            if(!result.next())return LicenseReasonCode.LICENSE_SERVER_NOT_ACTIVATED;
            return result.getString(1).equals(identity.licenseServerId().toString())&&result.getString(2).equals(identity.publicKeyFingerprint())?LicenseReasonCode.LICENSE_VALID:LicenseReasonCode.LICENSE_SERVER_IDENTITY_MISMATCH;
        }catch(Exception exception){return LicenseReasonCode.STATE_INTEGRITY_FAILURE;}
    }

    public Path stageValidatedRestore(Path backup,Path target,LicenseServerIdentity identity){
        Path source=backup.toAbsolutePath().normalize(),destination=target.toAbsolutePath().normalize();
        if(Files.exists(destination))throw new IllegalArgumentException("Restore target already exists");
        var reason=validateForOriginalIdentity(source,identity);if(reason!=LicenseReasonCode.LICENSE_VALID)throw new SqliteStorageException("Restore validation failed: "+reason,null);
        Path partial=destination.resolveSibling(destination.getFileName()+".partial");
        try{
            if(Files.exists(partial))throw new IllegalArgumentException("Partial restore target already exists");
            Path parent=destination.getParent();if(parent!=null)Files.createDirectories(parent);
            Files.copy(source,partial,StandardCopyOption.COPY_ATTRIBUTES);
            if(!integrityOk(partial)||validateForOriginalIdentity(partial,identity)!=LicenseReasonCode.LICENSE_VALID)throw new SqliteStorageException("Staged restore validation failed",null);
            try{Files.move(partial,destination,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(partial,destination);}
            return destination;
        }catch(RuntimeException|java.io.IOException exception){try{Files.deleteIfExists(partial);}catch(java.io.IOException cleanup){exception.addSuppressed(cleanup);}if(exception instanceof RuntimeException runtime)throw runtime;throw new SqliteStorageException("Restore staging failed",exception);}
    }

    private static boolean integrityOk(Path file){try(var connection=DriverManager.getConnection("jdbc:sqlite:file:"+file.toAbsolutePath()+"?mode=ro");var statement=connection.createStatement();var result=statement.executeQuery("PRAGMA integrity_check")){return result.next()&&"ok".equalsIgnoreCase(result.getString(1));}catch(Exception exception){return false;}}
}
