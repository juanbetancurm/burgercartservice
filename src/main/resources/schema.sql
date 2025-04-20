-- Create cart table if not exists
CREATE TABLE IF NOT EXISTS carts (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     user_id VARCHAR(255) NOT NULL,
    total DOUBLE NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT DEFAULT 0
    );

-- Create cart_items table if not exists
CREATE TABLE IF NOT EXISTS cart_items (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          cart_id BIGINT NOT NULL,
                                          article_id BIGINT NOT NULL,
                                          article_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    price DOUBLE NOT NULL,
    subtotal DOUBLE NOT NULL,
    version BIGINT DEFAULT 0,
    FOREIGN KEY (cart_id) REFERENCES carts(id),
    UNIQUE KEY unique_cart_article (cart_id, article_id)
    );