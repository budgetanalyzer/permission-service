-- =============================================================================
-- New permissions for currency-service and transaction-service statement formats
-- Replaces hasRole('ADMIN') workarounds with proper permission-based authorization
-- =============================================================================
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    -- Statement format management (transaction-service)
    ('statementformats:read', 'Read Statement Formats', 'statementformat', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('statementformats:write', 'Write Statement Formats', 'statementformat', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('statementformats:delete', 'Delete Statement Formats', 'statementformat', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Currency management (currency-service)
    ('currencies:read', 'Read Currencies', 'currency', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('currencies:write', 'Write Currencies', 'currency', 'write', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- ADMIN gets all 5 new permissions
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('ADMIN', 'statementformats:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'statementformats:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'statementformats:delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'currencies:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'currencies:write', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- USER gets statementformats:read and currencies:read
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('USER', 'statementformats:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'currencies:read', CURRENT_TIMESTAMP, 'SYSTEM');
