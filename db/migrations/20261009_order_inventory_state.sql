-- Run against the application's database. Existing orders remain legacy state 0.
-- Safe to rerun: check metadata before adding the column.
SET @inventory_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'inventory_state'),
  'SELECT 1',
  'ALTER TABLE orders ADD COLUMN inventory_state TINYINT NOT NULL DEFAULT 0'
);
PREPARE inventory_stmt FROM @inventory_ddl;
EXECUTE inventory_stmt;
DEALLOCATE PREPARE inventory_stmt;