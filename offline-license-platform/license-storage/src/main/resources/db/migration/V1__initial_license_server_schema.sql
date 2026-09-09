CREATE TABLE license_server_identity (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    server_id TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    public_key_fingerprint TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    metadata_json TEXT NOT NULL,
    state_sequence INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE commercial_license (
    license_id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    envelope_json TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    valid_until TEXT NOT NULL,
    grace_until TEXT NOT NULL,
    imported_at TEXT NOT NULL,
    active INTEGER NOT NULL CHECK (active IN (0, 1))
);
CREATE UNIQUE INDEX one_active_commercial_license ON commercial_license(active) WHERE active = 1;

CREATE TABLE activation_certificate (
    activation_id TEXT PRIMARY KEY,
    license_id TEXT NOT NULL REFERENCES commercial_license(license_id),
    server_id TEXT NOT NULL,
    public_key_fingerprint TEXT NOT NULL,
    envelope_json TEXT NOT NULL,
    issued_at TEXT NOT NULL,
    active INTEGER NOT NULL CHECK (active IN (0, 1))
);

CREATE TABLE local_revocation (
    revocation_id TEXT PRIMARY KEY,
    artifact_type TEXT NOT NULL,
    artifact_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    effective_at TEXT NOT NULL,
    imported_at TEXT NOT NULL,
    UNIQUE (artifact_type, artifact_id)
);

CREATE TABLE product_instance (
    instance_id TEXT PRIMARY KEY,
    public_key TEXT NOT NULL,
    public_key_fingerprint TEXT NOT NULL,
    product_id TEXT NOT NULL,
    deployment_type TEXT NOT NULL,
    environment_type TEXT NOT NULL,
    application_version TEXT NOT NULL,
    metadata_json TEXT NOT NULL,
    state TEXT NOT NULL,
    registered_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    decommissioned_at TEXT
);
CREATE INDEX product_instance_environment_idx ON product_instance(environment_type, state);

CREATE TABLE named_user_assignment (
    assignment_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    account_type TEXT NOT NULL,
    product_id TEXT,
    category TEXT,
    assigned_at TEXT NOT NULL,
    released_at TEXT,
    cooldown_until TEXT,
    UNIQUE(user_id, product_id, category)
);

CREATE TABLE user_lease (
    lease_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    account_type TEXT NOT NULL,
    instance_id TEXT NOT NULL REFERENCES product_instance(instance_id),
    session_id TEXT NOT NULL,
    acquired_at TEXT NOT NULL,
    heartbeat_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    released_at TEXT,
    revoked_at TEXT,
    UNIQUE(instance_id, session_id)
);
CREATE INDEX live_user_lease_idx ON user_lease(expires_at, released_at, revoked_at);

CREATE TABLE registered_agent (
    agent_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    instance_id TEXT NOT NULL REFERENCES product_instance(instance_id),
    registered_at TEXT NOT NULL,
    status TEXT NOT NULL
);

CREATE TABLE agent_lease (
    lease_id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL REFERENCES registered_agent(agent_id),
    execution_id TEXT NOT NULL,
    instance_id TEXT NOT NULL REFERENCES product_instance(instance_id),
    capability TEXT NOT NULL,
    acquired_at TEXT NOT NULL,
    heartbeat_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    released_at TEXT,
    revoked_at TEXT,
    UNIQUE(agent_id, execution_id)
);
CREATE INDEX live_agent_lease_idx ON agent_lease(expires_at, released_at, revoked_at);

CREATE TABLE meter_usage (
    customer_id TEXT NOT NULL,
    meter_id TEXT NOT NULL,
    period_start TEXT NOT NULL,
    period_end TEXT NOT NULL,
    used_amount INTEGER NOT NULL CHECK (used_amount >= 0),
    licensed_limit INTEGER NOT NULL CHECK (licensed_limit >= 0),
    PRIMARY KEY(customer_id, meter_id, period_start)
);

CREATE TABLE usage_consumption (
    request_id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    instance_id TEXT NOT NULL,
    meter_id TEXT NOT NULL,
    period_start TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    accepted INTEGER NOT NULL CHECK (accepted IN (0, 1)),
    decided_at TEXT NOT NULL,
    dimensions_json TEXT NOT NULL
);

CREATE TABLE trusted_time_state (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    highest_accepted_time TEXT NOT NULL,
    state_sequence INTEGER NOT NULL,
    checkpoint_hash TEXT NOT NULL
);

CREATE TABLE cached_entitlement_issue (
    certificate_id TEXT PRIMARY KEY,
    license_id TEXT NOT NULL REFERENCES commercial_license(license_id),
    instance_id TEXT NOT NULL REFERENCES product_instance(instance_id),
    envelope_json TEXT NOT NULL,
    issued_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked_at TEXT
);

CREATE TABLE request_nonce (
    instance_id TEXT NOT NULL REFERENCES product_instance(instance_id),
    nonce TEXT NOT NULL,
    request_time TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    PRIMARY KEY(instance_id, nonce)
);

CREATE TABLE audit_event (
    audit_id TEXT PRIMARY KEY,
    occurred_at TEXT NOT NULL,
    customer_id TEXT,
    license_id TEXT,
    instance_id TEXT,
    operation TEXT NOT NULL,
    entitlement TEXT,
    allowed INTEGER NOT NULL CHECK (allowed IN (0, 1)),
    reason_code TEXT NOT NULL,
    context_json TEXT NOT NULL,
    previous_hash TEXT,
    event_hash TEXT NOT NULL UNIQUE
);
CREATE INDEX audit_occurred_at_idx ON audit_event(occurred_at, audit_id);
