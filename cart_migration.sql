-- V002__Cart_Session_Management.sql - MySQL 5.7+ Compatible Version
-- Migration script for enhanced cart session management features

-- ===============================================
-- CART TABLE ENHANCEMENTS
-- ===============================================

-- Check if columns exist before adding them
SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND COLUMN_NAME = 'created_at') = 0,
        'ALTER TABLE carts ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;',
        'SELECT "Column created_at already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND COLUMN_NAME = 'session_id') = 0,
        'ALTER TABLE carts ADD COLUMN session_id VARCHAR(32);',
        'SELECT "Column session_id already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND COLUMN_NAME = 'version') = 0,
        'ALTER TABLE carts ADD COLUMN version INT DEFAULT 0;',
        'SELECT "Column version already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND COLUMN_NAME = 'expiry_warning_sent') = 0,
        'ALTER TABLE carts ADD COLUMN expiry_warning_sent BOOLEAN DEFAULT FALSE;',
        'SELECT "Column expiry_warning_sent already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Update existing records to have created_at if null
UPDATE carts
SET created_at = last_updated
WHERE created_at IS NULL;

-- Generate session IDs for existing carts
UPDATE carts
SET session_id = CONCAT('cart_', SUBSTRING(MD5(CONCAT(id, user_id, UNIX_TIMESTAMP())), 1, 16))
WHERE session_id IS NULL;

-- Ensure created_at is not null
ALTER TABLE carts
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Ensure session_id is not null
ALTER TABLE carts
    MODIFY COLUMN session_id VARCHAR(32) NOT NULL;

-- ===============================================
-- CART ITEMS TABLE ENHANCEMENTS
-- ===============================================

-- Add audit fields to cart items
SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'cart_items'
         AND COLUMN_NAME = 'created_at') = 0,
        'ALTER TABLE cart_items ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;',
        'SELECT "Column cart_items.created_at already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'cart_items'
         AND COLUMN_NAME = 'updated_at') = 0,
        'ALTER TABLE cart_items ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;',
        'SELECT "Column cart_items.updated_at already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Update existing records
UPDATE cart_items
SET created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

-- Make audit fields not null
ALTER TABLE cart_items
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE cart_items
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ===============================================
-- INDEXES FOR PERFORMANCE
-- ===============================================

-- Check and create indexes only if they don't exist
SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND INDEX_NAME = 'idx_carts_user_status') = 0,
        'CREATE INDEX idx_carts_user_status ON carts(user_id, status);',
        'SELECT "Index idx_carts_user_status already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND INDEX_NAME = 'idx_carts_session_id') = 0,
        'CREATE INDEX idx_carts_session_id ON carts(session_id);',
        'SELECT "Index idx_carts_session_id already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND INDEX_NAME = 'idx_carts_status_last_updated') = 0,
        'CREATE INDEX idx_carts_status_last_updated ON carts(status, last_updated);',
        'SELECT "Index idx_carts_status_last_updated already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND INDEX_NAME = 'idx_carts_last_updated') = 0,
        'CREATE INDEX idx_carts_last_updated ON carts(last_updated);',
        'SELECT "Index idx_carts_last_updated already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'cart_items'
         AND INDEX_NAME = 'idx_cart_items_cart_article') = 0,
        'CREATE INDEX idx_cart_items_cart_article ON cart_items(cart_id, article_id);',
        'SELECT "Index idx_cart_items_cart_article already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'cart_items'
         AND INDEX_NAME = 'idx_cart_items_article_id') = 0,
        'CREATE INDEX idx_cart_items_article_id ON cart_items(article_id);',
        'SELECT "Index idx_cart_items_article_id already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===============================================
-- NEW TABLES
-- ===============================================

-- Create cart_history table
CREATE TABLE IF NOT EXISTS cart_history (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            cart_id BIGINT NOT NULL,
                                            user_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT,
    event_data JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_cart_history_cart_id (cart_id),
    INDEX idx_cart_history_user_id (user_id),
    INDEX idx_cart_history_event_type (event_type),
    INDEX idx_cart_history_created_at (created_at),

    CONSTRAINT fk_cart_history_cart
    FOREIGN KEY (cart_id) REFERENCES carts(id)
    ON DELETE CASCADE
    );

-- Create cart_sessions table
CREATE TABLE IF NOT EXISTS cart_sessions (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             session_id VARCHAR(32) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    cart_id BIGINT,
    jwt_token_hash VARCHAR(64),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    INDEX idx_cart_sessions_session_id (session_id),
    INDEX idx_cart_sessions_user_id (user_id),
    INDEX idx_cart_sessions_status_expires (status, expires_at),
    INDEX idx_cart_sessions_last_activity (last_activity),

    CONSTRAINT fk_cart_sessions_cart
    FOREIGN KEY (cart_id) REFERENCES carts(id)
                                                               ON DELETE SET NULL,

    CONSTRAINT chk_cart_session_status
    CHECK (status IN ('ACTIVE', 'EXPIRED', 'TERMINATED'))
    );

-- Create cart_config table
CREATE TABLE IF NOT EXISTS cart_config (
                                           config_key VARCHAR(100) PRIMARY KEY,
    config_value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    );

-- ===============================================
-- CONSTRAINTS (Add only if they don't exist)
-- ===============================================

-- Check and add constraints
SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND CONSTRAINT_NAME = 'chk_cart_status') = 0,
        'ALTER TABLE carts ADD CONSTRAINT chk_cart_status CHECK (status IN (''ACTIVE'', ''ABANDONED'', ''COMPLETED''));',
        'SELECT "Constraint chk_cart_status already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'cart_items'
         AND CONSTRAINT_NAME = 'chk_cart_item_quantity') = 0,
        'ALTER TABLE cart_items ADD CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0);',
        'SELECT "Constraint chk_cart_item_quantity already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'cart_items'
         AND CONSTRAINT_NAME = 'chk_cart_item_price') = 0,
        'ALTER TABLE cart_items ADD CONSTRAINT chk_cart_item_price CHECK (price >= 0);',
        'SELECT "Constraint chk_cart_item_price already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND CONSTRAINT_NAME = 'chk_cart_total') = 0,
        'ALTER TABLE carts ADD CONSTRAINT chk_cart_total CHECK (total >= 0);',
        'SELECT "Constraint chk_cart_total already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'carts'
         AND CONSTRAINT_NAME = 'uk_carts_session_id') = 0,
        'ALTER TABLE carts ADD CONSTRAINT uk_carts_session_id UNIQUE (session_id);',
        'SELECT "Constraint uk_carts_session_id already exists";'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===============================================
-- DATA CLEANUP AND OPTIMIZATION
-- ===============================================

-- Clean up any orphaned cart items
DELETE ci FROM cart_items ci
LEFT JOIN carts c ON ci.cart_id = c.id
WHERE c.id IS NULL;

-- Update cart totals to ensure consistency
UPDATE carts c
SET total = (
    SELECT COALESCE(SUM(ci.price * ci.quantity), 0)
    FROM cart_items ci
    WHERE ci.cart_id = c.id
);

-- ===============================================
-- INITIAL CONFIGURATION DATA
-- ===============================================

INSERT INTO cart_config (config_key, config_value, description) VALUES
                                                                    ('cart_expiry_hours', '24', 'Hours after which inactive carts are considered expired'),
                                                                    ('cart_warning_hours', '4', 'Hours before expiry when warnings should be sent'),
                                                                    ('max_items_per_cart', '50', 'Maximum number of items allowed in a single cart'),
                                                                    ('cleanup_interval_hours', '6', 'How often to run cart cleanup procedures'),
                                                                    ('max_carts_per_user', '1', 'Maximum number of active carts per user')
    ON DUPLICATE KEY UPDATE
                         config_value = VALUES(config_value),
                         updated_at = CURRENT_TIMESTAMP;

-- ===============================================
-- MIGRATION VERIFICATION
-- ===============================================

-- Verify the migration completed successfully
SELECT
    'Migration V002 completed' as status,
    COUNT(*) as total_carts,
    COUNT(CASE WHEN session_id IS NOT NULL THEN 1 END) as carts_with_session_id,
    COUNT(CASE WHEN created_at IS NOT NULL THEN 1 END) as carts_with_created_at
FROM carts;