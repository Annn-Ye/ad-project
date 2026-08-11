ALTER TABLE users ADD COLUMN version INT NOT NULL DEFAULT 1;

CREATE TABLE admin_grants (
    user_id CHAR(36) NOT NULL,
    active BOOLEAN NOT NULL,
    version INT NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    granted_by CHAR(36) NULL,
    revoked_at DATETIME(6) NULL,
    revoked_by CHAR(36) NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_admin_grants_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_admin_grants_granted_by FOREIGN KEY (granted_by) REFERENCES users (id),
    CONSTRAINT fk_admin_grants_revoked_by FOREIGN KEY (revoked_by) REFERENCES users (id)
);

CREATE TABLE admin_audit_events (
    id CHAR(36) NOT NULL,
    actor_id CHAR(36) NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id CHAR(36) NOT NULL,
    before_state TEXT NULL,
    after_state TEXT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX idx_admin_audit_occurred ON admin_audit_events (occurred_at, id);
CREATE INDEX idx_admin_audit_actor ON admin_audit_events (actor_id, occurred_at);
CREATE INDEX idx_admin_audit_target ON admin_audit_events (target_type, target_id, occurred_at);

CREATE TABLE moderation_cases (
    id CHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id CHAR(36) NOT NULL,
    author_id CHAR(36) NULL,
    content_snapshot TEXT NOT NULL,
    report_reason VARCHAR(500) NOT NULL,
    report_count INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_moderation_source UNIQUE (source_type, source_id),
    CONSTRAINT fk_moderation_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_moderation_status_created ON moderation_cases (status, created_at, id);
CREATE INDEX idx_moderation_author ON moderation_cases (author_id, created_at);
