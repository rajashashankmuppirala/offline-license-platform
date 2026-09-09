#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

step() {
  printf '%02d. %s\n' "$1" "$2"
}

steps() {
  step 1 "Generate a vendor Ed25519 key pair"
  step 2 "Issue and cryptographically verify the A Inc. master commercial license (LICENSE_VALID)"
  step 3 "Start the first License Server with a new installation identity"
  step 4 "Assert the first server is UNACTIVATED (LICENSE_NOT_ACTIVATED)"
  step 5 "Generate the first server activation request"
  step 6 "Issue and cryptographically verify the vendor activation certificate (LICENSE_VALID)"
  step 7 "Import the commercial license and activation certificate"
  step 8 "Assert the first server is ACTIVE (LICENSE_VALID) with exactly one decision audit"
  step 9 "Tamper with concurrent-user capacity without resigning"
  step 10 "Assert the modified license is rejected (LICENSE_SIGNATURE_INVALID) with exactly one decision audit"
  step 11 "Initialize a second independent License Server identity"
  step 12 "Assert first-server activation is rejected by the second server (LICENSE_SERVER_IDENTITY_MISMATCH) with exactly one decision audit"
  step 13 "Register the PROD Kubernetes logical instance (LICENSE_VALID) with exactly one decision audit"
  step 14 "Register a UAT logical instance (LICENSE_VALID) with exactly one decision audit"
  step 15 "Attempt a second PROD registration at capacity one"
  step 16 "Assert the second PROD registration is rejected (ENVIRONMENT_INSTANCE_LIMIT_EXCEEDED) with exactly one decision audit"
  step 17 "Replace 3-to-20 PROD replicas using one shared logical identity"
  step 18 "Assert rollout, reschedule, and restored namespace consume one environment allocation"
  step 19 "Acquire concurrent-user leases across PROD and UAT to the customer-wide limit (LICENSE_VALID)"
  step 20 "Attempt one concurrent-user lease beyond the shared limit"
  step 21 "Assert the excess lease is rejected (CONCURRENT_USER_LIMIT_EXCEEDED) with exactly one decision audit"
  step 22 "Expire one concurrent-user lease without extending it across restart"
  step 23 "Assert a replacement lease recovers capacity (LICENSE_VALID) with exactly one decision audit"
  step 24 "Assign named users to the configured customer-wide limit (LICENSE_VALID)"
  step 25 "Assert the next named assignment is rejected (NAMED_USER_LIMIT_EXCEEDED) with exactly one decision audit"
  step 26 "Assert an unlicensed feature is rejected (FEATURE_NOT_ENTITLED) with exactly one decision audit"
  step 27 "Register AI agents to the configured customer-wide limit (LICENSE_VALID)"
  step 28 "Assert one additional agent registration is rejected (AGENT_REGISTRATION_LIMIT_EXCEEDED) with exactly one decision audit"
  step 29 "Acquire agent execution leases to the concurrency limit (LICENSE_VALID)"
  step 30 "Assert one additional simultaneous execution is rejected (AGENT_CONCURRENCY_LIMIT_EXCEEDED) with exactly one decision audit"
  step 31 "Consume weighted customer-wide monthly AEU atomically (LICENSE_VALID)"
  step 32 "Reach the AEU quota exactly without over-allocation (LICENSE_VALID)"
  step 33 "Assert additional AEU consumption is rejected (AI_USAGE_LIMIT_EXCEEDED) with exactly one decision audit"
  step 34 "Issue a signed cached entitlement and stop the License Server"
  step 35 "Assert product and entitled-feature checks work from a valid bound cache (LICENSE_VALID)"
  step 36 "Assert registration, lease, and metering allocation is denied during outage (LICENSE_SERVER_UNAVAILABLE)"
  step 37 "Restart the same License Server installation"
  step 38 "Assert identity and authoritative state survived restart without lease extension"
  step 39 "Advance beyond expiry and restricted grace and assert denial (LICENSE_EXPIRED) with exactly one decision audit"
  step 40 "Move time backward beyond tolerance and assert denial (CLOCK_ROLLBACK_DETECTED) with exactly one decision audit"
}

if [ "${1:-}" = "--list" ]; then
  steps
  exit 0
fi

run_group() {
  selector=$1
  first=$2
  last=$3
  (
    cd "$ROOT"
    mvn -q \
      -Dtest="$selector" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dsurefire.skipAfterFailureCount=1 \
      test
  )
  steps | sed -n "${first},${last}p"
}

# A numbered step is printed only after its observable scenario group passes.
# set -e therefore stops at the first unmet cryptographic, stable-reason,
# persistence, concurrency, outage, trusted-time, or one-to-one audit assertion.
run_group 'CommercialLicenseCryptoTest,ActivationIntegrationTest,DomainDecisionAuditTest' 1 12
run_group 'InstanceLifecycleIntegrationTest,EnvironmentRegistrationRaceTest,LogicalReplicaRegistrationIntegrationTest,DomainDecisionAuditTest' 13 18
run_group 'ConcurrentUserLeaseIntegrationTest,NamedUserAssignmentIntegrationTest,ProductEntitlementServiceTest,DomainDecisionAuditTest' 19 26
run_group 'AgentRegistrationRaceTest,AgentExecutionLeaseRaceTest,DomainDecisionAuditTest' 27 30
run_group 'UsageMeterRaceTest,DomainDecisionAuditTest' 31 33
run_group 'CachedEntitlementCryptoTest,CachedEntitlementServiceTest,LicenseOutageRecoveryProcessTest,DomainDecisionAuditTest' 34 36
run_group 'LicenseLifecycleProcessTest,ConcurrentUserLeaseIntegrationTest' 37 38
run_group 'LicenseValidationServiceTest,TrustedTimeRepositoryTest,EngineAuthorizationBoundaryTest,DomainDecisionAuditTest' 39 40
