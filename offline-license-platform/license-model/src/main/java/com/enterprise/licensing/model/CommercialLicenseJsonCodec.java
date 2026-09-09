package com.enterprise.licensing.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class CommercialLicenseJsonCodec {
    public static final String CURRENT_SCHEMA = "1.0";
    private final ObjectMapper mapper;
    private final LegacyLicenseMapper legacyMapper;

    public CommercialLicenseJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
        this.legacyMapper = new LegacyLicenseMapper();
    }

    public CommercialLicense read(byte[] json, LegacyDefaults legacyDefaults) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode version = root.get("schemaVersion");
            if (version == null) return legacyMapper.map(root, legacyDefaults);
            CommercialLicenseSchema.validate(json);
            return mapper.treeToValue(root, CommercialLicense.class);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Malformed commercial license", e); }
    }

    public CommercialLicense readVersioned(byte[] json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || root.get("schemaVersion") == null) throw new IllegalArgumentException("schemaVersion is required");
            CommercialLicenseSchema.validate(json);
            return mapper.treeToValue(root, CommercialLicense.class);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Malformed commercial license", e); }
    }

    public record LegacyDefaults(String licenseId, String customerId, ActivationPolicy activationPolicy) {
        public LegacyDefaults {
            licenseId = Validation.required(licenseId, "licenseId");
            customerId = Validation.required(customerId, "customerId");
            if (activationPolicy == null) throw new IllegalArgumentException("activationPolicy is required");
        }
    }
}
