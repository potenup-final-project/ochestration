CREATE TABLE IF NOT EXISTS payments
(
    payment_id       VARCHAR(36)  NOT NULL,
    merchant_id      VARCHAR(100) NOT NULL,
    order_id         VARCHAR(100) NOT NULL,
    amount           BIGINT       NOT NULL,
    currency         VARCHAR(10)  NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL,
    requested_at     DATETIME(6)  NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    approved_provider VARCHAR(50)  NULL,
    provider_tx_id   VARCHAR(200) NULL,
    approved_at      DATETIME(6)  NULL,
    canceled_at      DATETIME(6)  NULL,
    failure_code     VARCHAR(100) NULL,
    failure_category VARCHAR(50)  NULL,
    failure_message  VARCHAR(500) NULL,
    metadata         TEXT         NULL,
    cancel_reason    VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (payment_id),
    INDEX idx_payments_merchant_id (merchant_id),
    INDEX idx_payments_order_id (order_id),
    INDEX idx_payments_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_attempts
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id       VARCHAR(36)  NOT NULL,
    attempt_no       INT          NOT NULL,
    provider         VARCHAR(50)  NOT NULL,
    result           VARCHAR(50)  NOT NULL,
    failure_category VARCHAR(50)  NULL,
    failure_code     VARCHAR(100) NULL,
    failure_message  VARCHAR(500) NULL,
    provider_tx_id   VARCHAR(200) NULL,
    attempted_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_attempts_payment_id (payment_id),
    CONSTRAINT fk_payment_attempts_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_selection_summaries
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id               VARCHAR(36)  NOT NULL,
    selected_primary_provider VARCHAR(50)  NULL,
    selected_primary_reason  VARCHAR(500) NOT NULL,
    fallback_reason          VARCHAR(500) NULL,
    final_approved_provider  VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uq_payment_selection_summaries_payment_id (payment_id),
    CONSTRAINT fk_payment_selection_summaries_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_selection_initial_candidates
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    selection_summary_id BIGINT      NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    sort_order          INT         NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_selection_initial_candidates_summary_id (selection_summary_id),
    CONSTRAINT fk_selection_initial_candidates_summary
        FOREIGN KEY (selection_summary_id) REFERENCES payment_selection_summaries (id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_selection_filtered_out_providers
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    selection_summary_id BIGINT      NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    reason              VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_selection_filtered_out_summary_id (selection_summary_id),
    CONSTRAINT fk_selection_filtered_out_summary
        FOREIGN KEY (selection_summary_id) REFERENCES payment_selection_summaries (id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS provider_connections
(
    provider_connection_id VARCHAR(36)  NOT NULL,
    provider               VARCHAR(50)  NOT NULL,
    merchant_id            VARCHAR(100) NOT NULL,
    display_name           VARCHAR(200) NOT NULL,
    status                 VARCHAR(50)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (provider_connection_id),
    UNIQUE INDEX uq_provider_connections_provider (provider),
    INDEX idx_provider_connections_merchant_id (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS provider_health
(
    provider      VARCHAR(50) NOT NULL,
    health_status VARCHAR(50) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (provider)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
