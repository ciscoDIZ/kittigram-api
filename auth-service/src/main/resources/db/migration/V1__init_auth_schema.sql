CREATE SCHEMA IF NOT EXISTS auth;

CREATE SEQUENCE auth."refresh_tokens_SEQ" START WITH 1 INCREMENT BY 50;

CREATE TABLE auth.refresh_tokens
(
    id         BIGINT       PRIMARY KEY DEFAULT nextval('auth."refresh_tokens_SEQ"'),
    token      VARCHAR(255) NOT NULL UNIQUE,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX refresh_tokens_user_id_idx ON auth.refresh_tokens (user_id);
CREATE INDEX refresh_tokens_email_idx   ON auth.refresh_tokens (email);