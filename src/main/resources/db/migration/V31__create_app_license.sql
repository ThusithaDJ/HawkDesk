CREATE TABLE app_license (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_key      VARCHAR(255) NOT NULL,
    shop_name        VARCHAR(255),
    license_server_url VARCHAR(500),
    machine_id       VARCHAR(500),
    activated_date   DATE NOT NULL
);
