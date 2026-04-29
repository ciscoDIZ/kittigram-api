CREATE SEQUENCE chat."blocked_participants_SEQ" START WITH 1 INCREMENT BY 50;

CREATE TABLE chat.blocked_participants (
    id                BIGINT       PRIMARY KEY DEFAULT nextval('chat."blocked_participants_SEQ"'),
    organization_id   BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    reason            TEXT,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_blocked_org_user UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_blocked_user_id ON chat.blocked_participants (user_id);
