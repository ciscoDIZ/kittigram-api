CREATE SCHEMA IF NOT EXISTS users;

CREATE SEQUENCE users."users_SEQ" START WITH 1 INCREMENT BY 50;

CREATE TABLE users.users
(
    id                          BIGINT       PRIMARY KEY DEFAULT nextval('users."users_SEQ"'),
    email                       VARCHAR(255) NOT NULL,
    password_hash               VARCHAR(255) NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    surname                     VARCHAR(255) NOT NULL,
    birthdate                   DATE,
    status                      VARCHAR(50)  NOT NULL,
    role                        VARCHAR(50)  NOT NULL,
    activation_token            VARCHAR(255),
    activation_token_expires_at TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX users_email_uq ON users.users (email);
CREATE UNIQUE INDEX users_activation_token_uq ON users.users (activation_token)
    WHERE activation_token IS NOT NULL;
