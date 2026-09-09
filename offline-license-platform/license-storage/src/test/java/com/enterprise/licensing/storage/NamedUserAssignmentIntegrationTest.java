package com.enterprise.licensing.storage;

import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.SqliteUserLicenseRepository;
import com.enterprise.licensing.storage.sqlite.*;
import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class NamedUserAssignmentIntegrationTest {
 @TempDir Path temp;
 @Test void enforcesGlobalCategoryProductAndReassignmentCooldown(){var database=new SqliteDatabase(temp.resolve("named.db"));var users=new SqliteUserLicenseRepository(database,new SqliteImmediateTransactionRunner(database));var now=Instant.parse("2026-01-01T00:00:00Z");
  assertThat(users.assign("a1","u1",AccountType.HUMAN,ProductId.VALIDATOR,UserCategory.REPORTING,2,1,1,now).accepted()).isTrue();
  assertThat(users.assign("a2","u2",AccountType.HUMAN,ProductId.VALIDATOR,UserCategory.REPORTING,2,1,1,now).reasonCode()).isEqualTo(LicenseReasonCode.NAMED_USER_LIMIT_EXCEEDED);
  assertThat(users.release("u1",ProductId.VALIDATOR,UserCategory.REPORTING,now,Duration.ofDays(30),false,null).accepted()).isTrue();
  assertThat(users.assign("a3","u1",AccountType.HUMAN,ProductId.VALIDATOR,UserCategory.REPORTING,2,1,1,now.plusSeconds(1)).reasonCode()).isEqualTo(LicenseReasonCode.NAMED_USER_REASSIGNMENT_COOLDOWN);
  assertThat(users.release("u1",ProductId.VALIDATOR,UserCategory.REPORTING,now,Duration.ofDays(30),true,"UNAPPROVED").reasonCode()).isEqualTo(LicenseReasonCode.INVALID_REQUEST);
  assertThat(users.assign("a4","u1",AccountType.HUMAN,ProductId.VALIDATOR,UserCategory.REPORTING,2,1,1,now.plus(Duration.ofDays(31))).accepted()).isTrue();
 }
}
