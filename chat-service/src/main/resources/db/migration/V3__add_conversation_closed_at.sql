ALTER TABLE chat.conversations ADD COLUMN closed_at TIMESTAMP;

CREATE INDEX idx_conversations_closed_at ON chat.conversations (closed_at);