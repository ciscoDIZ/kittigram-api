CREATE SEQUENCE chat."conversations_SEQ" START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE chat."messages_SEQ"      START WITH 1 INCREMENT BY 50;

CREATE TABLE chat.conversations (
    id                  BIGINT       PRIMARY KEY DEFAULT nextval('chat."conversations_SEQ"'),
    intake_request_id   BIGINT       NOT NULL UNIQUE,
    user_id             BIGINT       NOT NULL,
    organization_id     BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    last_message_at     TIMESTAMP
);

CREATE INDEX idx_conversations_user_id ON chat.conversations (user_id);
CREATE INDEX idx_conversations_org_id  ON chat.conversations (organization_id);

CREATE TABLE chat.messages (
    id                  BIGINT       PRIMARY KEY DEFAULT nextval('chat."messages_SEQ"'),
    conversation_id     BIGINT       NOT NULL REFERENCES chat.conversations(id),
    sender_id           BIGINT       NOT NULL,
    sender_type         VARCHAR(20)  NOT NULL,
    content             TEXT         NOT NULL,
    created_at          TIMESTAMP    NOT NULL
);

CREATE INDEX idx_messages_conversation_id ON chat.messages (conversation_id, created_at);