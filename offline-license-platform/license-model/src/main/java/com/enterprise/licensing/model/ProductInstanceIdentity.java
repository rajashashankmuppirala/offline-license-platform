package com.enterprise.licensing.model;
import java.time.Instant;
public record ProductInstanceIdentity(String instanceId,EncodedPublicKey publicKey,String publicKeyFingerprint,Instant createdAt){public ProductInstanceIdentity{instanceId=Validation.required(instanceId,"instanceId");publicKeyFingerprint=Validation.required(publicKeyFingerprint,"publicKeyFingerprint");if(publicKey==null||createdAt==null)throw new IllegalArgumentException("identity fields required");}}
