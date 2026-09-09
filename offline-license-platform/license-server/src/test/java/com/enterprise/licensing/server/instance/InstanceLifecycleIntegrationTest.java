package com.enterprise.licensing.server.instance;

import com.enterprise.licensing.crypto.Ed25519Keys;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.SqliteProductInstanceRepository;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class InstanceLifecycleIntegrationTest {
 @TempDir Path temp; private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
 @Test void reconnectRestartDecommissionAndReuseFollowStableIdentityRules(){var database=new SqliteDatabase(temp.resolve("instances.db"));var repository=new SqliteProductInstanceRepository(database,new SqliteImmediateTransactionRunner(database),new ObjectMapper());var key=Ed25519Keys.generate();var prod=request("prod",key,DeploymentType.KUBERNETES);
  var first=repository.register(prod,Ed25519Keys.fingerprint(key.getPublic()),1,1,now);var reconnect=repository.register(prod,Ed25519Keys.fingerprint(key.getPublic()),1,1,now.plusSeconds(10));
  assertThat(first.accepted()).isTrue();assertThat(first.instance().state()).isEqualTo(InstanceState.REGISTERED);assertThat(reconnect.instance().registeredAt()).isEqualTo(first.instance().registeredAt());assertThat(repository.list()).hasSize(1);
  var impostor=Ed25519Keys.generate();assertThat(repository.register(request("prod",impostor,DeploymentType.KUBERNETES),Ed25519Keys.fingerprint(impostor.getPublic()),1,1,now).reasonCode()).isEqualTo(LicenseReasonCode.INSTANCE_AUTHENTICATION_FAILED);
  assertThat(repository.transition("prod",InstanceState.REGISTERED,InstanceState.ACTIVE,1,now.plusSeconds(15),false).accepted()).isTrue();
  assertThat(repository.transition("prod",InstanceState.ACTIVE,InstanceState.DECOMMISSIONED,1,now.plusSeconds(20),false).accepted()).isTrue();
  assertThat(repository.register(prod,Ed25519Keys.fingerprint(key.getPublic()),1,1,now.plusSeconds(30)).accepted()).isFalse();
  var replacement=Ed25519Keys.generate();assertThat(repository.register(request("replacement",replacement,DeploymentType.TOMCAT),Ed25519Keys.fingerprint(replacement.getPublic()),1,1,now.plusSeconds(30)).accepted()).isTrue();assertThat(repository.list()).hasSize(2);
 }
 private InstanceRegistrationRequest request(String id,java.security.KeyPair key,DeploymentType deployment){return new InstanceRegistrationRequest(id,new EncodedPublicKey("Ed25519","X.509",Ed25519Keys.encodePublic(key.getPublic())),ProductId.VALIDATOR,deployment,EnvironmentType.PRODUCTION,"1",Map.of());}
}
