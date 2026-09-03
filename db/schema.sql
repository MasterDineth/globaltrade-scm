-- =============================================================================
-- GlobalTrade Logistics Corporation -- Supply Chain Management Platform
-- MySQL 8.0+ DDL
-- =============================================================================
-- Hand-written and DBA-controlled rather than JPA-schema-generated (see
-- persistence.xml, jakarta.persistence.schema-generation.database.action =
-- "none") so that foreign keys, indexes and default-value semantics are
-- explicit and reviewable rather than implicit in provider behavior that can
-- change between EclipseLink versions.
--
-- Every table, column and index below corresponds 1:1 to a JPA @Table /
-- @Column / @Index / @UniqueConstraint / @JoinColumn annotation in
-- scm-ejb/src/main/java/com/globaltrade/scm/entity -- if the two ever
-- disagree, this file is wrong, not the entity (the entity is what actually
-- executes against the database and therefore what has to be kept
-- authoritative).
--
-- InnoDB throughout: this is a transactional, foreign-keyed, high-integrity
-- schema (customs/trade audit trail, financial-adjacent inventory counts) --
-- MyISAM's lack of foreign-key enforcement and transaction support would be
-- actively wrong here, not just a missed optimization.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS globaltrade_scm
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE globaltrade_scm;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- vendor
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS vendor;
CREATE TABLE vendor (
    vendor_id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(150)    NOT NULL,
    country           VARCHAR(2)      NOT NULL COMMENT 'ISO 3166-1 alpha-2',
    contact_email     VARCHAR(150)    NULL,
    performance_score DOUBLE          NULL DEFAULT 0.0,
    active            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)     NOT NULL,
    version           BIGINT          NOT NULL DEFAULT 0 COMMENT 'JPA @Version optimistic lock',
    KEY idx_vendor_country (country)
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
    shipment_id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tracking_number     VARCHAR(64)  NOT NULL,
    origin_country      VARCHAR(2)   NOT NULL,
    destination_country VARCHAR(2)   NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    weight_kg           DOUBLE       NULL,
    estimated_delivery  DATETIME(6)  NULL,
    actual_delivery     DATETIME(6)  NULL,
    vendor_id           BIGINT UNSIGNED NOT NULL,
    carrier_id          BIGINT UNSIGNED NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_shipment_tracking_number UNIQUE (tracking_number),
    KEY idx_shipment_status (status),
    CONSTRAINT fk_shipment_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (vendor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_shipment_carrier FOREIGN KEY (carrier_id) REFERENCES carrier (carrier_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;
-- ON DELETE RESTRICT (not CASCADE) on both FKs: a vendor or carrier being
-- deactivated/removed from active use must never silently delete or orphan
-- historical shipment records -- those records are the audit trail this
-- platform exists partly to guarantee (see AuditLogEntry below). Application
-- code deactivates a Vendor/Carrier (the `active` flag) rather than deleting
-- the row for exactly this reason.

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
    metric_type           VARCHAR(64)  NOT NULL COMMENT 'e.g. ON_TIME_DELIVERY_RATE, MANUAL_REVIEW_SCORE',
    value                 DOUBLE       NOT NULL,
    recorded_at           DATETIME(6)  NOT NULL,
    KEY idx_metric_vendor (vendor_id),
    KEY idx_metric_type (metric_type),
    CONSTRAINT fk_metric_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (vendor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- customs_document  (one-to-one with shipment: unique FK)
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
    CONSTRAINT fk_customs_document_shipment FOREIGN KEY (shipment_id) REFERENCES shipment (shipment_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- audit_log_entry  (append-only; no UPDATE/DELETE path exists in application code)
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
-- failed_operation  (dead-letter table for ExceptionRecoveryManager)
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
-- system_user  (backing store for SupplyChainLoginModule)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS system_user;
CREATE TABLE system_user (
    system_user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL COMMENT 'iterations:base64(salt):base64(hash) -- see SecurityUtil',
    full_name       VARCHAR(150) NULL,
    role            VARCHAR(32)  NOT NULL COMMENT 'must match a com.globaltrade.scm.common.enums.UserRole constant',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_user_username UNIQUE (username)
) ENGINE = InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Seed data
-- =============================================================================

-- One demo account per role. Password hashes below were generated with the
-- ACTUAL com.globaltrade.scm.security.SecurityUtil.hashPassword algorithm
-- (PBKDF2WithHmacSHA256, 120,000 iterations) and verified round-trip against
-- SecurityUtil.verifyPassword before being embedded here, so these are real,
-- working credentials against a freshly-loaded database -- not placeholders.
-- Change every one of these before any deployment beyond a local/demo
-- environment.
--
--   username        password         role
--   --------        --------         ----
--   admin           Admin@12345      ADMIN
--   jcoordinator    Coord@12345      LOGISTICS_COORDINATOR
--   cagent          Customs@12345    CUSTOMS_AGENT
--   wmanager        Warehouse@12345  WAREHOUSE_MANAGER
--   vrep            Vendor@12345     VENDOR_REPRESENTATIVE
--   customer1       Customer@12345   CUSTOMER
INSERT INTO system_user (username, password_hash, full_name, role, active) VALUES
    ('admin', '120000:TVOfKy2TvlA+nEupu7hjfg==:muiBlGZ+VpuOSYF8b4ay3XjO1VIFqRKhHqhgRZB4JsY=', 'System Administrator', 'ADMIN', TRUE),
    ('jcoordinator', '120000:GtB7J/Kle3V0qETX/+f4UQ==:IEpaqou6NEMOz92n5F2icoYT3YQqcDarC9gptDxrmh8=', 'Jordan Coordinator', 'LOGISTICS_COORDINATOR', TRUE),
    ('cagent', '120000:GStlQvmrciXf4MdpeiAKpw==:nDqh2gVztLf9rJzw2ad1Hg4Zls72cEsXIcFKNBsX8oI=', 'Casey Agent', 'CUSTOMS_AGENT', TRUE),
    ('wmanager', '120000:L7IiZAnc71nceASwe9PbOA==:cNRbwpGMYWnivBYe9r977tvIaVv2HSdzmJEJhHbPM8s=', 'Wren Manager', 'WAREHOUSE_MANAGER', TRUE),
    ('vrep', '120000:zBnP2Wa/yBJRzw0XWfoagw==:dEKLkvV3hGuK+ccx1uyEyziXpVZahN7VyYRfvja3H4A=', 'Vendor Representative Demo', 'VENDOR_REPRESENTATIVE', TRUE),
    ('customer1', '120000:igbn8Fj+s9nU+hMnfuoMlw==:WRqTrN9L/WN15Gu97fGo1Uz7HZrR9cWdFdWuSvVS3M8=', 'Demo Customer', 'CUSTOMER', TRUE);

INSERT INTO vendor (name, country, contact_email, performance_score, active, created_at, version) VALUES
    ('Pacific Rim Textiles Ltd.', 'CN', 'trade@pacificrimtextiles.example', 87.5, TRUE, NOW(6), 0),
    ('Nordic Components AB', 'SE', 'sales@nordiccomponents.example', 94.2, TRUE, NOW(6), 0),
    ('Andes Fresh Produce S.A.', 'CL', 'export@andesfresh.example', 78.0, TRUE, NOW(6), 0);

INSERT INTO carrier (name, code, api_endpoint, region, active) VALUES
    ('Global Ocean Freight', 'GOF', 'https://api.globaloceanfreight.example/v1', 'GLOBAL', TRUE),
    ('TransAtlantic Air Cargo', 'TAAC', 'https://api.transatlanticaircargo.example/v2', 'ATLANTIC', TRUE),
    ('PacBridge Logistics', 'PBL', 'https://api.pacbridge.example/v1', 'PACIFIC', TRUE);

INSERT INTO inventory_item (sku, description, quantity_on_hand, reorder_threshold, warehouse_location, last_restocked_at, version) VALUES
    ('SKU-TEX-1001', 'Cotton fabric roll, 100m', 420, 150, 'WH-EU-01', NOW(6), 0),
    ('SKU-ELE-2002', 'Precision bearing assembly, 50-unit case', 60, 75, 'WH-EU-01', NOW(6), 0),
    ('SKU-AGR-3003', 'Frozen berry mix, 20kg case', 900, 200, 'WH-SA-02', NOW(6), 0);

-- Seed data intentionally does not include shipment/customs_document rows:
-- both are more meaningfully created through the application (registerShipment
-- / OrderProcessingServiceBean) so that their timer-scheduling side effects
-- (ShipmentStatusUpdateTimerBean's poll, CustomsDeadlineTimerBean's
-- programmatic per-document timer) are exercised the same way they would be
-- in production, rather than starting from rows the timers never "saw"
-- created.
