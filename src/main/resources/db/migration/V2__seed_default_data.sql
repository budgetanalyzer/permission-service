-- =============================================================================
-- SYSTEM user for audit trail on seeded data
-- =============================================================================
INSERT INTO users (id, idp_sub, email, display_name, created_at, created_by)
VALUES ('SYSTEM', 'system|internal', 'system@budgetanalyzer.local', 'System', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- Default roles: ADMIN and USER
-- =============================================================================
INSERT INTO roles (id, name, description, is_system, created_at, created_by) VALUES
    ('ADMIN', 'Administrator', 'Full access to all resources and management operations', true, CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'User', 'Standard access to own resources', true, CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- Default permissions (14)
-- =============================================================================
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    -- Transaction management
    ('transactions:read', 'Read Transactions', 'transaction', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:write', 'Write Transactions', 'transaction', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:delete', 'Delete Transactions', 'transaction', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Account management
    ('accounts:read', 'Read Accounts', 'account', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('accounts:write', 'Write Accounts', 'account', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('accounts:delete', 'Delete Accounts', 'account', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Budget management
    ('budgets:read', 'Read Budgets', 'budget', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('budgets:write', 'Write Budgets', 'budget', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('budgets:delete', 'Delete Budgets', 'budget', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- User management
    ('users:read', 'Read Users', 'user', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('users:write', 'Write Users', 'user', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('users:delete', 'Delete Users', 'user', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Role management
    ('roles:read', 'View Roles', 'role', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('roles:write', 'Create/Modify Roles', 'role', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Audit
    ('audit:read', 'Read Audit Logs', 'audit', 'read', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- ADMIN gets all 15 permissions
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by)
SELECT 'ADMIN', id, CURRENT_TIMESTAMP, 'SYSTEM' FROM permissions;

-- =============================================================================
-- USER gets basic resource access (6 permissions)
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('USER', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'transactions:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'accounts:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'accounts:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'budgets:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'budgets:write', CURRENT_TIMESTAMP, 'SYSTEM');
