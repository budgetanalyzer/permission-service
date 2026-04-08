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
-- Default permissions (17)
-- =============================================================================
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    -- Transaction management
    ('transactions:read', 'Read Transactions', 'transaction', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:write', 'Write Transactions', 'transaction', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:delete', 'Delete Transactions', 'transaction', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:read:any', 'Read Any User''s Transactions', 'transaction', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:write:any', 'Write Any User''s Transactions', 'transaction', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:delete:any', 'Delete Any User''s Transactions', 'transaction', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- User management
    ('users:read', 'Read Users', 'user', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('users:write', 'Write Users', 'user', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('users:delete', 'Delete Users', 'user', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Statement format management
    ('statementformats:read', 'Read Statement Formats', 'statementformat', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('statementformats:write', 'Write Statement Formats', 'statementformat', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('statementformats:delete', 'Delete Statement Formats', 'statementformat', 'delete', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Currency management
    ('currencies:read', 'Read Currencies', 'currency', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('currencies:write', 'Write Currencies', 'currency', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),

    -- Saved view management
    ('views:read', 'Read Saved Views', 'view', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('views:write', 'Write Saved Views', 'view', 'write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('views:delete', 'Delete Saved Views', 'view', 'delete', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- GRANT-TIME ACTION HIERARCHY INVARIANT
-- =============================================================================
-- For any resource/scope, both :write and :delete require :read. :write and
-- :delete are INDEPENDENT of each other -- a role may legitimately hold
-- {read, write}, {read, delete}, or {read, write, delete}. The only forbidden
-- shapes are ones that hold a modifying action without :read.
--
--        write    delete
--           \    /
--            read
--
-- The rule runs within a scope: :write:any implies :read:any but says nothing
-- about the unscoped :write.
--
-- This is enforced by convention in this migration and in any future migration
-- that inserts into or deletes from role_permissions. It is NOT enforced at
-- runtime. Downstream services, controller @PreAuthorize annotations, and UI
-- route guards all do literal permission checks that depend on this invariant
-- holding at issue time.
--
-- If you add a :write grant, add the matching :read on the same role in the
-- same migration. If you add a :delete grant, add the matching :read on the
-- same role in the same migration. Adding :write does NOT require adding
-- :delete, and vice versa. If you revoke :read, first revoke any :write and
-- :delete the role holds.
--
-- See docs/authorization-model.md#permission-action-hierarchy for rationale.
-- =============================================================================

-- =============================================================================
-- ADMIN gets the 14 non-view permissions
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('ADMIN', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:read:any', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:write:any', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:delete:any', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'users:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'users:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'users:delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'statementformats:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'statementformats:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'statementformats:delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'currencies:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'currencies:write', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- USER gets own-resource access (8 permissions)
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('USER', 'transactions:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'transactions:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'transactions:delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'views:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'views:write', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'views:delete', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'statementformats:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'currencies:read', CURRENT_TIMESTAMP, 'SYSTEM');
