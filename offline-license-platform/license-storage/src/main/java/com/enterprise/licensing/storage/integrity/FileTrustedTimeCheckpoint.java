package com.enterprise.licensing.storage.integrity;

import com.enterprise.licensing.crypto.CanonicalJson;
import com.enterprise.licensing.engine.identity.LicenseServerIdentityService;
import com.enterprise.licensing.engine.time.*;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.Base64;

public final class FileTrustedTimeCheckpoint implements TrustedTimeRepository {
    private final TrustedTimeRepository database;
    private final LicenseServerIdentityService identity;
    private final Path file;
    private final ObjectMapper mapper;
    private final CanonicalJson canonical;
    private boolean recoveryRequired;
    private LicenseReasonCode recoveryReason = LicenseReasonCode.RECOVERY_REQUIRED;

    public FileTrustedTimeCheckpoint(TrustedTimeRepository database, LicenseServerIdentityService identity,
                                     Path file, ObjectMapper mapper) {
        this.database=database;this.identity=identity;this.file=file.toAbsolutePath().normalize();
        this.mapper=mapper;this.canonical=new CanonicalJson(mapper);
    }

    @Override public synchronized TrustedTimeResult observe(Instant current, Duration tolerance) {
        if (recoveryRequired) return denied();
        Checkpoint checkpoint=read();
        if (checkpoint!=null&&!verify(checkpoint))
            return requireRecovery(LicenseReasonCode.STATE_INTEGRITY_FAILURE,checkpoint.highest(),checkpoint.sequence());
        var persisted=database.current();
        if (persisted.isPresent()&&!persisted.get().accepted()) {
            var value=persisted.get();
            return requireRecovery(value.reasonCode(),value.highestAcceptedTime(),value.sequence());
        }
        if (checkpoint!=null&&persisted.isPresent()&&checkpoint.sequence()>persisted.get().sequence())
            return requireRecovery(LicenseReasonCode.RECOVERY_REQUIRED,persisted.get().highestAcceptedTime(),persisted.get().sequence());
        var result=database.observe(current,tolerance);
        if (!result.accepted()) {
            if (result.reasonCode()==LicenseReasonCode.CLOCK_ROLLBACK_DETECTED
                    || result.reasonCode()==LicenseReasonCode.STATE_INTEGRITY_FAILURE)
                return requireRecovery(result.reasonCode(),result.highestAcceptedTime(),result.sequence());
            return result;
        }
        // Compatibility for simple repository adapters that cannot expose current() before mutation.
        if (checkpoint!=null&&persisted.isEmpty()&&checkpoint.sequence()>=result.sequence())
            return requireRecovery(LicenseReasonCode.RECOVERY_REQUIRED,result.highestAcceptedTime(),result.sequence());
        write(result.highestAcceptedTime(),result.sequence());
        return result;
    }

    @Override public synchronized boolean recoveryRequired(){return recoveryRequired;}

    @Override public synchronized TrustedTimeResult reconcile(Instant baseline,long ignored){
        Checkpoint checkpoint=read();
        long minimum=checkpoint==null?0:checkpoint.sequence();
        var result=database.reconcile(baseline,minimum);
        write(result.highestAcceptedTime(),result.sequence());
        recoveryRequired=false;recoveryReason=LicenseReasonCode.RECOVERY_REQUIRED;
        return result;
    }

    private TrustedTimeResult requireRecovery(LicenseReasonCode reason,Instant highest,long sequence){
        recoveryRequired=true;recoveryReason=reason;
        return new TrustedTimeResult(false,reason,highest,sequence);
    }
    private TrustedTimeResult denied(){
        Checkpoint checkpoint=read();
        return new TrustedTimeResult(false,recoveryReason,checkpoint==null?Instant.EPOCH:checkpoint.highest(),checkpoint==null?0:checkpoint.sequence());
    }
    private Checkpoint read(){try{return Files.exists(file)?mapper.readValue(file.toFile(),Checkpoint.class):null;}catch(Exception e){return new Checkpoint(Instant.EPOCH,Long.MAX_VALUE,"invalid");}}
    private boolean verify(Checkpoint c){try{return identity.verifiesInstallationProof(content(c.highest(),c.sequence()),Base64.getUrlDecoder().decode(c.signature()));}catch(Exception e){return false;}}
    private void write(Instant highest,long sequence){try{Files.createDirectories(file.getParent());String signature=Base64.getUrlEncoder().withoutPadding().encodeToString(identity.signInstallationProof(content(highest,sequence)));var value=new Checkpoint(highest,sequence,signature);Path temp=Files.createTempFile(file.getParent(),".trusted-time-",".tmp");try{mapper.writeValue(temp.toFile(),value);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}catch(Exception e){throw new IllegalStateException("Unable to persist trusted-time checkpoint",e);}}
    private byte[]content(Instant highest,long sequence){return canonical.canonicalize(java.util.Map.of("format","TRUSTED_TIME_CHECKPOINT_V1","licenseServerId",identity.publicIdentity().licenseServerId(),"highest",highest,"sequence",sequence));}
    private record Checkpoint(Instant highest,long sequence,String signature){}
}
