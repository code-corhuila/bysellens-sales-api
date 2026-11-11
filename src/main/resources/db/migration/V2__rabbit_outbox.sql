CREATE TABLE rabbit_outbox (id varchar(100) PRIMARY KEY, routing_key varchar(100) NOT NULL, payload text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), sent_at timestamptz);
CREATE INDEX rabbit_outbox_pending_idx ON rabbit_outbox(created_at) WHERE sent_at IS NULL;
