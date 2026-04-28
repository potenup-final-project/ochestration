CREATE TABLE IF NOT EXISTS merchant_api_keys (
    key_id           VARCHAR(36)   NOT NULL,
    merchant_id      VARCHAR(100)  NOT NULL,
    key_hash         VARCHAR(64)   NOT NULL,
    key_prefix       VARCHAR(20)   NOT NULL,
    environment      VARCHAR(10)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    scopes           VARCHAR(500)  NOT NULL DEFAULT 'PAYMENT_WRITE,PAYMENT_READ',
    description      VARCHAR(255),
    expired_at       TIMESTAMP,
    grace_expired_at TIMESTAMP,
    revoked_at       TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL,
    last_used_at     TIMESTAMP,
    PRIMARY KEY (key_id),
    UNIQUE (key_hash)
);
