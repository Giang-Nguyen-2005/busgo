-- M2: store only SHA-256 digests; consumed/revoked tokens are deleted.
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_expiry (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
