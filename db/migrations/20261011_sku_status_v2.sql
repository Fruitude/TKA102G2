-- MySQL 8. Stop the app and take a database backup first.
-- Requires old status codes 0..6. Never run against already-remapped data.
CREATE TABLE IF NOT EXISTS sku_schema_migrations (
  version VARCHAR(64) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DELIMITER $$
CREATE PROCEDURE migrate_sku_status_20261011()
BEGIN
  DECLARE already_done INT DEFAULT 0;
  DECLARE column_exists INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  SELECT COUNT(*) INTO already_done FROM sku_schema_migrations WHERE version='20261011_sku_status_v2';
  IF already_done > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration already applied; stop, do not remap statuses again';
  END IF;
  IF EXISTS(SELECT 1 FROM product_sku WHERE status IS NULL OR status NOT BETWEEN 0 AND 6) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected old status code; review data before migration';
  END IF;
  -- A pre-existing backup means an interrupted/previous attempt; manual review is required.
  CREATE TABLE sku_status_backup_20261011 AS SELECT sku_id,status,updated_at FROM product_sku;
  SELECT COUNT(*) INTO column_exists FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='product' AND column_name='auto_restock_enabled';
  IF column_exists=0 THEN
    ALTER TABLE product ADD COLUMN auto_restock_enabled TINYINT NOT NULL DEFAULT 0;
  END IF;
  -- DDL commits independently in MySQL. Mapping and marker below are atomic.
  START TRANSACTION;
  UPDATE product SET auto_restock_enabled=CASE WHEN status=1 THEN 1 ELSE 0 END;
  UPDATE product_sku SET status=CASE status WHEN 3 THEN 6 WHEN 4 THEN 7 WHEN 5 THEN 4 WHEN 6 THEN 5 ELSE status END;
  INSERT INTO sku_schema_migrations(version) VALUES('20261011_sku_status_v2');
  COMMIT;
END$$
DELIMITER ;
CALL migrate_sku_status_20261011();
DROP PROCEDURE migrate_sku_status_20261011;

-- If any statement fails, STOP. DDL/backup may already exist; do not blindly rerun.
-- Recover only after stopping the app and checking the saved snapshot/new transactions.
