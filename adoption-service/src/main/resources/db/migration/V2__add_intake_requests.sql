CREATE SEQUENCE adoption."intake_requests_SEQ" START WITH 1 INCREMENT BY 50;

CREATE TABLE adoption.intake_requests (
    id                       BIGINT       PRIMARY KEY DEFAULT nextval('adoption."intake_requests_SEQ"'),
    user_id                  BIGINT       NOT NULL,
    target_organization_id   BIGINT       NOT NULL,
    cat_name                 VARCHAR(255) NOT NULL,
    cat_age                  INT          NOT NULL,
    region                   VARCHAR(255) NOT NULL,
    city                     VARCHAR(255) NOT NULL,
    vaccinated               BOOLEAN      NOT NULL,
    description              TEXT,
    status                   VARCHAR(50)  NOT NULL,
    rejection_reason         VARCHAR(255),
    created_at               TIMESTAMP    NOT NULL,
    decided_at               TIMESTAMP
);

CREATE INDEX idx_intake_requests_user_id ON adoption.intake_requests (user_id);
CREATE INDEX idx_intake_requests_org_id  ON adoption.intake_requests (target_organization_id);