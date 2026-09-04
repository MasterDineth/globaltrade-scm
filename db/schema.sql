-- =============================================================================
-- GlobalTrade Logistics Corporation -- Supply Chain Management Platform
-- MySQL 8.0+ DDL
-- =============================================================================

CREATE DATABASE IF NOT EXISTS globaltrade_scm
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE globaltrade_scm;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- country
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS country;
CREATE TABLE country (
    country_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(2)   NOT NULL,
    name       VARCHAR(150) NOT NULL,
    CONSTRAINT uq_country_code UNIQUE (code)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- user_role
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS user_role;
CREATE TABLE user_role (
    role_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(32) NOT NULL,
    CONSTRAINT uq_user_role_name UNIQUE (name)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- metric_type
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS metric_type;
CREATE TABLE metric_type (
    metric_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(64) NOT NULL,
    CONSTRAINT uq_metric_type_name UNIQUE (name)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- vendor
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS vendor;
CREATE TABLE vendor (
    vendor_id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(150)    NOT NULL,
    country_id        BIGINT UNSIGNED NOT NULL,
    contact_email     VARCHAR(150)    NULL,
    performance_score DOUBLE          NULL DEFAULT 0.0,
    active            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)     NOT NULL,
    version           BIGINT          NOT NULL DEFAULT 0,
    KEY idx_vendor_country (country_id),
    CONSTRAINT fk_vendor_country FOREIGN KEY (country_id) REFERENCES country (country_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- carrier
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS carrier;
CREATE TABLE carrier (
    carrier_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(20)  NOT NULL,
    api_endpoint  VARCHAR(255) NULL,
    region        VARCHAR(50)  NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_carrier_code UNIQUE (code)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- shipment
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS shipment;
CREATE TABLE shipment (
    shipment_id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tracking_number        VARCHAR(64)  NOT NULL,
    origin_country_id      BIGINT UNSIGNED NOT NULL,
    destination_country_id BIGINT UNSIGNED NOT NULL,
    status                 VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    weight_kg              DOUBLE       NULL,
    estimated_delivery     DATETIME(6)  NULL,
    actual_delivery        DATETIME(6)  NULL,
    vendor_id              BIGINT UNSIGNED NOT NULL,
    carrier_id             BIGINT UNSIGNED NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_shipment_tracking_number UNIQUE (tracking_number),
    KEY idx_shipment_status (status),
    CONSTRAINT fk_shipment_origin_country FOREIGN KEY (origin_country_id) REFERENCES country (country_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_shipment_dest_country FOREIGN KEY (destination_country_id) REFERENCES country (country_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_shipment_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (vendor_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_shipment_carrier FOREIGN KEY (carrier_id) REFERENCES carrier (carrier_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- inventory_item
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS inventory_item;
CREATE TABLE inventory_item (
    inventory_item_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sku                VARCHAR(64)  NOT NULL,
    description        VARCHAR(255) NULL,
    quantity_on_hand   INT          NOT NULL DEFAULT 0,
    reorder_threshold  INT          NOT NULL DEFAULT 0,
    warehouse_location VARCHAR(100) NULL,
    last_restocked_at  DATETIME(6)  NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_sku UNIQUE (sku),
    CONSTRAINT chk_inventory_quantity_non_negative CHECK (quantity_on_hand >= 0)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- performance_metric
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS performance_metric;
CREATE TABLE performance_metric (
    performance_metric_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vendor_id             BIGINT UNSIGNED NOT NULL,
    metric_type_id        BIGINT UNSIGNED NOT NULL,
    value                 DOUBLE       NOT NULL,
    recorded_at           DATETIME(6)  NOT NULL,
    KEY idx_metric_vendor (vendor_id),
    KEY idx_metric_type (metric_type_id),
    CONSTRAINT fk_metric_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (vendor_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_metric_type FOREIGN KEY (metric_type_id) REFERENCES metric_type (metric_type_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- customs_document
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS customs_document;
CREATE TABLE customs_document (
    customs_document_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    shipment_id          BIGINT UNSIGNED NOT NULL,
    document_type         VARCHAR(32)  NOT NULL,
    status                 VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    submission_deadline    DATETIME(6)  NULL,
    submitted_at           DATETIME(6)  NULL,
    approved_at            DATETIME(6)  NULL,
    compliance_notes       VARCHAR(2000) NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_customs_document_shipment UNIQUE (shipment_id),
    KEY idx_customs_deadline (submission_deadline),
    CONSTRAINT fk_customs_document_shipment FOREIGN KEY (shipment_id) REFERENCES shipment (shipment_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- audit_log_entry
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log_entry;
CREATE TABLE audit_log_entry (
    audit_log_entry_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    entity_name         VARCHAR(150) NOT NULL,
    entity_id           VARCHAR(64)  NULL,
    action               VARCHAR(100) NOT NULL,
    performed_by         VARCHAR(100) NOT NULL,
    timestamp            DATETIME(6)  NOT NULL,
    details               VARCHAR(4000) NULL,
    KEY idx_audit_timestamp (timestamp),
    KEY idx_audit_entity (entity_name)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- failed_operation
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS failed_operation;
CREATE TABLE failed_operation (
    failed_operation_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    operation_type        VARCHAR(100) NOT NULL,
    payload                VARCHAR(4000) NULL,
    failure_reason         VARCHAR(1000) NULL,
    retry_count            INT          NOT NULL DEFAULT 0,
    last_attempt_at        DATETIME(6)  NULL,
    resolved               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_failed_op_resolved (resolved)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- system_user
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS system_user;
CREATE TABLE system_user (
    system_user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NULL,
    role_id         BIGINT UNSIGNED NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES user_role (role_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Seed data
-- =============================================================================

INSERT INTO country (country_id, code, name) VALUES 
(1, 'CN', 'China'),
(2, 'SE', 'Sweden'),
(3, 'CL', 'Chile'),
(4, 'US', 'United States'),
(5, 'GB', 'United Kingdom'),
(6, 'SG', 'Singapore');

INSERT INTO user_role (role_id, name) VALUES 
(1, 'ADMIN'),
(2, 'LOGISTICS_COORDINATOR'),
(3, 'CUSTOMS_AGENT'),
(4, 'WAREHOUSE_MANAGER'),
(5, 'VENDOR_REPRESENTATIVE'),
(6, 'CUSTOMER');

INSERT INTO metric_type (metric_type_id, name) VALUES 
(1, 'ON_TIME_DELIVERY_RATE'),
(2, 'DEFECT_RATE'),
(3, 'MANUAL_REVIEW_SCORE');

INSERT INTO system_user (username, password_hash, full_name, role_id, active) VALUES
    ('admin', '120000:TVOfKy2TvlA+nEupu7hjfg==:muiBlGZ+VpuOSYF8b4ay3XjO1VIFqRKhHqhgRZB4JsY=', 'System Administrator', 1, TRUE),
    ('jcoordinator', '120000:GtB7J/Kle3V0qETX/+f4UQ==:IEpaqou6NEMOz92n5F2icoYT3YQqcDarC9gptDxrmh8=', 'Jordan Coordinator', 2, TRUE),
    ('cagent', '120000:GStlQvmrciXf4MdpeiAKpw==:nDqh2gVztLf9rJzw2ad1Hg4Zls72cEsXIcFKNBsX8oI=', 'Casey Agent', 3, TRUE),
    ('wmanager', '120000:L7IiZAnc71nceASwe9PbOA==:cNRbwpGMYWnivBYe9r977tvIaVv2HSdzmJEJhHbPM8s=', 'Wren Manager', 4, TRUE),
    ('vrep', '120000:zBnP2Wa/yBJRzw0XWfoagw==:dEKLkvV3hGuK+ccx1uyEyziXpVZahN7VyYRfvja3H4A=', 'Vendor Representative Demo', 5, TRUE),
    ('customer1', '120000:igbn8Fj+s9nU+hMnfuoMlw==:WRqTrN9L/WN15Gu97fGo1Uz7HZrR9cWdFdWuSvVS3M8=', 'Demo Customer', 6, TRUE);

INSERT INTO vendor (vendor_id, name, country_id, contact_email, performance_score, active, created_at, version) VALUES
    (1, 'Pacific Rim Textiles Ltd.', 1, 'trade@pacificrimtextiles.example', 87.5, TRUE, NOW(6), 0),
    (2, 'Nordic Components AB', 2, 'sales@nordiccomponents.example', 94.2, TRUE, NOW(6), 0),
    (3, 'Andes Fresh Produce S.A.', 3, 'export@andesfresh.example', 78.0, TRUE, NOW(6), 0);

INSERT INTO carrier (carrier_id, name, code, api_endpoint, region, active) VALUES
    (1, 'Global Ocean Freight', 'GOF', 'https://api.globaloceanfreight.example/v1', 'GLOBAL', TRUE),
    (2, 'TransAtlantic Air Cargo', 'TAAC', 'https://api.transatlanticaircargo.example/v2', 'ATLANTIC', TRUE),
    (3, 'PacBridge Logistics', 'PBL', 'https://api.pacbridge.example/v1', 'PACIFIC', TRUE);

INSERT INTO inventory_item (inventory_item_id, sku, description, quantity_on_hand, reorder_threshold, warehouse_location, last_restocked_at, version) VALUES
    (1, 'SKU-TEX-1001', 'Cotton fabric roll, 100m', 420, 150, 'WH-EU-01', NOW(6), 0),
    (2, 'SKU-ELE-2002', 'Precision bearing assembly, 50-unit case', 60, 75, 'WH-EU-01', NOW(6), 0),
    (3, 'SKU-AGR-3003', 'Frozen berry mix, 20kg case', 900, 200, 'WH-SA-02', NOW(6), 0);
