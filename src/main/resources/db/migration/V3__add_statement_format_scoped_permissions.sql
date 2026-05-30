-- =============================================================================
-- Statement format scoped permissions
-- =============================================================================
-- Statement formats now follow the same scoped permission model as transactions:
-- - statementformats:read/write covers formats visible to the current user.
-- - statementformats:read:any/write:any covers all user and system formats.
--
-- The grant-time action hierarchy still applies within each scope: write:any
-- requires read:any on the same role. No statementformats:delete permission is
-- added because no delete workflow exists.
-- =============================================================================

INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    ('statementformats:read:any', 'Read Any User''s Statement Formats', 'statementformat', 'read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('statementformats:write:any', 'Write Any User''s Statement Formats', 'statementformat', 'write', CURRENT_TIMESTAMP, 'SYSTEM');

INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('ADMIN', 'statementformats:read:any', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('USER', 'statementformats:write', CURRENT_TIMESTAMP, 'SYSTEM');

UPDATE role_permissions
SET permission_id = 'statementformats:write:any',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE role_id = 'ADMIN'
  AND permission_id = 'statementformats:write';
