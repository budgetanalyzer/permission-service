-- =============================================================================
-- SYSTEM user for audit trail on seeded data
-- =============================================================================
INSERT INTO users (id, auth0_sub, email, display_name, created_at, created_by)
VALUES ('SYSTEM', 'system|internal', 'system@budgetanalyzer.local', 'System', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- Default roles (hierarchical structure for proper governance)
-- =============================================================================
--
-- Role Hierarchy:
--   SYSTEM_ADMIN - Platform owner, manages permission system (database-only assignment)
--   ORG_ADMIN    - Organization administrator, manages users and basic roles
--   MANAGER      - Team oversight, approvals, read access to team data
--   ACCOUNTANT   - Professional access to delegated accounts
--   AUDITOR      - Read-only compliance access
--   USER         - Self-service access to own resources
--
INSERT INTO roles (id, name, description, created_at, created_by) VALUES
    ('SYSTEM_ADMIN', 'System Administrator', 'Platform administration - manages roles, permissions, and can assign admin roles. Cannot be assigned via API.', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'Organization Administrator', 'Organization administration - manages users and can assign basic roles within organization', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('MANAGER', 'Manager', 'Team oversight - can view team data and approve operations', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ACCOUNTANT', 'Accountant', 'Professional access - can manage delegated user accounts', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('AUDITOR', 'Auditor', 'Compliance access - read-only with full visibility', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'User', 'Self-service - access to own resources only', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- Default permissions
-- =============================================================================
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    -- User management
    ('users:read', 'Read Users', 'user', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('users:write', 'Write Users', 'user', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('users:delete', 'Delete Users', 'user', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Transaction management
    ('transactions:read', 'Read Transactions', 'transaction', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:write', 'Write Transactions', 'transaction', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:delete', 'Delete Transactions', 'transaction', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:approve', 'Approve Transactions', 'transaction', 'approve', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:bulk', 'Bulk Transaction Operations', 'transaction', 'bulk', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Account management
    ('accounts:read', 'Read Accounts', 'account', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('accounts:write', 'Write Accounts', 'account', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('accounts:delete', 'Delete Accounts', 'account', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('accounts:delegate', 'Delegate Accounts', 'account', 'delegate', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Budget management
    ('budgets:read', 'Read Budgets', 'budget', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('budgets:write', 'Write Budgets', 'budget', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('budgets:delete', 'Delete Budgets', 'budget', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Audit and reporting
    ('audit:read', 'Read Audit Logs', 'audit', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('reports:export', 'Export Reports', 'report', 'export', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- =============================================================================
    -- Meta-permissions (govern the authorization system itself)
    -- =============================================================================
    ('roles:read', 'View Roles', 'role', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('roles:write', 'Create/Modify Roles', 'role', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('roles:delete', 'Delete Roles', 'role', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('permissions:read', 'View Permissions', 'permission', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('permissions:write', 'Create/Modify Permissions', 'permission', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Role assignment permissions (critical for governance)
    -- assign-basic: Can assign USER, ACCOUNTANT, AUDITOR
    -- assign-elevated: Can assign MANAGER, ORG_ADMIN (requires higher privilege)
    ('user-roles:assign-basic', 'Assign Basic Roles', 'user-role', 'assign-basic', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('user-roles:assign-elevated', 'Assign Elevated Roles', 'user-role', 'assign-elevated', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('user-roles:revoke', 'Revoke User Roles', 'user-role', 'revoke', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- Default role-permission mappings
-- =============================================================================

-- SYSTEM_ADMIN gets ALL permissions (including meta-permissions)
-- This role can only be assigned directly in the database, not via API
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by, granted_at, granted_by)
SELECT 'SYSTEM_ADMIN', id, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM' FROM permissions;

-- ORG_ADMIN: Business oversight + basic role assignment
-- Cannot modify roles/permissions or assign elevated roles
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by, granted_at, granted_by) VALUES
    -- User management
    ('ORG_ADMIN', 'users:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'users:write', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'users:delete', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    -- Business data (read-only for oversight)
    ('ORG_ADMIN', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'accounts:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'budgets:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'audit:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'reports:export', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    -- Role management (limited)
    ('ORG_ADMIN', 'roles:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'user-roles:assign-basic', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ORG_ADMIN', 'user-roles:revoke', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');

-- MANAGER: Team oversight and approvals (no role management)
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by, granted_at, granted_by) VALUES
    ('MANAGER', 'users:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('MANAGER', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('MANAGER', 'transactions:approve', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('MANAGER', 'accounts:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('MANAGER', 'budgets:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('MANAGER', 'reports:export', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');

-- ACCOUNTANT: Professional access to delegated accounts
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by, granted_at, granted_by) VALUES
    ('ACCOUNTANT', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ACCOUNTANT', 'transactions:write', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ACCOUNTANT', 'transactions:approve', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ACCOUNTANT', 'accounts:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ACCOUNTANT', 'reports:export', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');

-- AUDITOR: Read-only compliance access
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by, granted_at, granted_by) VALUES
    ('AUDITOR', 'users:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('AUDITOR', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('AUDITOR', 'accounts:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('AUDITOR', 'budgets:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('AUDITOR', 'audit:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('AUDITOR', 'reports:export', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');

-- USER: Self-service access to own resources
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by, granted_at, granted_by) VALUES
    ('USER', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'transactions:write', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'transactions:delete', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'accounts:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'accounts:write', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'accounts:delegate', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'budgets:read', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'budgets:write', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');
