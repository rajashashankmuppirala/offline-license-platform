package com.enterprise.licensing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.licensing.engine.user.NamedUserResult;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.repository.SqliteUserLicenseRepository;
import com.enterprise.licensing.storage.sqlite.SqliteDatabase;
import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NamedUserAssignmentRaceTest {
    @TempDir Path temp;
    @Test void exactlyOneAssignmentGetsFinalNamedSeat() {
        var database=new SqliteDatabase(temp.resolve("named.db"));
        var repository=new SqliteUserLicenseRepository(database,new SqliteImmediateTransactionRunner(database));
        var sequence=new AtomicInteger();var now=Instant.parse("2026-01-01T00:00:00Z");
        var results=ConcurrentTestSupport.race(2,()->{int id=sequence.incrementAndGet();return repository.assign("a-"+id,"u-"+id,AccountType.HUMAN,
                ProductId.VALIDATOR,UserCategory.REPORTING,1,1,1,now);});
        assertThat(results).filteredOn(NamedUserResult::accepted).hasSize(1);
        assertThat(results).filteredOn(result->!result.accepted()).singleElement().extracting(NamedUserResult::reasonCode)
                .isEqualTo(LicenseReasonCode.NAMED_USER_LIMIT_EXCEEDED);
    }
}
