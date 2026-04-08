-- =============================================================================
-- Cross-user transaction permissions (":any" scope)
-- Enables transaction-service to replace hasRole('ADMIN') with proper
-- permission-based authorization for cross-user code paths.
-- See: architecture-conversations/docs/plans/permission-based-authorization-cleanup.md
-- =============================================================================
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    ('transactions:read:any',   'Read Any User''s Transactions',   'transaction', 'read',   CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:write:any',  'Write Any User''s Transactions',  'transaction', 'write',  CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:delete:any', 'Delete Any User''s Transactions', 'transaction', 'delete', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- ADMIN gets all three; USER gets none (cross-user scope is admin-only)
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('ADMIN', 'transactions:read:any',   CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:write:any',  CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:delete:any', CURRENT_TIMESTAMP, 'SYSTEM');
