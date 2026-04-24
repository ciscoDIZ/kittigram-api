CREATE SCHEMA IF NOT EXISTS organization;

CREATE SEQUENCE organization."organizations_SEQ"        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE organization."organization_members_SEQ" START WITH 1 INCREMENT BY 50;

CREATE TABLE organization.organizations (
    id          BIGINT       PRIMARY KEY DEFAULT nextval('organization."organizations_SEQ"'),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    address     VARCHAR(255),
    city        VARCHAR(255),
    region      VARCHAR(255),
    country     VARCHAR(255),
    phone       VARCHAR(255),
    email       VARCHAR(255),
    logo_url    VARCHAR(255),
    status      VARCHAR(50)  NOT NULL,
    plan        VARCHAR(50)  NOT NULL,
    max_members INTEGER      NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE TABLE organization.organization_members (
    id              BIGINT      PRIMARY KEY DEFAULT nextval('organization."organization_members_SEQ"'),
    organization_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    role            VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    joined_at       TIMESTAMP   NOT NULL,
    CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);
