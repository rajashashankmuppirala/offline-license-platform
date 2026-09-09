package com.enterprise.licensing.crypto.vendor;

import com.enterprise.licensing.model.ArtifactType;
import com.enterprise.licensing.model.CommercialLicense;
import com.enterprise.licensing.model.CommercialLicenseSchema;
import com.enterprise.licensing.model.SignedEnvelope;
import java.security.KeyPair;

public final class CommercialLicenseSigner {
    private final PayloadSigner signer;
    public CommercialLicenseSigner(PayloadSigner signer) { this.signer = signer; }
    public SignedEnvelope sign(CommercialLicense license, KeyPair vendorKey) { return signer.sign(CommercialLicenseSchema.jsonTree(license), license.schemaVersion(), ArtifactType.COMMERCIAL_LICENSE, vendorKey); }
}
