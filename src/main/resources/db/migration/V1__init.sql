-- Coupons and their per-user redemptions.

CREATE TABLE coupon (
    id           UUID         PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    max_uses     INTEGER      NOT NULL,
    current_uses INTEGER      NOT NULL DEFAULT 0,
    country      VARCHAR(2)   NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_coupon_code UNIQUE (code),
    CONSTRAINT chk_max_uses_positive CHECK (max_uses >= 1),
    CONSTRAINT chk_current_uses_non_negative CHECK (current_uses >= 0),
    CONSTRAINT chk_current_within_max CHECK (current_uses <= max_uses)
);

-- Code is normalized to upper case by the application; a plain unique index is sufficient and
-- keeps lookups index-backed (WIOSNA and wiosna are stored identically).

CREATE TABLE coupon_redemption (
    id          UUID         PRIMARY KEY,
    coupon_id   UUID         NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    redeemed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_redemption_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    CONSTRAINT uq_redemption_user UNIQUE (coupon_id, user_id)
);

CREATE INDEX idx_redemption_coupon ON coupon_redemption (coupon_id);
