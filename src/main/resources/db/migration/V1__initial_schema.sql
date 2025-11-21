-- =============================================================================
-- Users table (authorization subjects, linked to Auth0)
-- =============================================================================
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    auth0_sub VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Soft delete fields
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_by VARCHAR(50)
);

CREATE INDEX idx_users_auth0_sub ON users(auth0_sub) WHERE deleted = false;
CREATE INDEX idx_users_email ON users(email) WHERE deleted = false;
-- Partial unique indexes to allow reuse after soft delete
CREATE UNIQUE INDEX users_auth0_sub_active ON users(auth0_sub) WHERE deleted = false;
CREATE UNIQUE INDEX users_email_active ON users(email) WHERE deleted = false;

COMMENT ON TABLE users IS 'Local user records linked to Auth0 for authorization';
COMMENT ON COLUMN users.id IS 'Internal user ID (e.g., usr_xxx)';
COMMENT ON COLUMN users.auth0_sub IS 'Auth0 subject identifier';
COMMENT ON COLUMN users.deleted IS 'Soft delete flag';

-- =============================================================================
-- Role definitions with hierarchy support
-- =============================================================================
CREATE TABLE roles (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_role_id VARCHAR(50) REFERENCES roles(id),
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Soft delete fields
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_by VARCHAR(50)
);

-- Partial unique index to allow role name reuse after soft delete
CREATE UNIQUE INDEX roles_name_active ON roles(name) WHERE deleted = false;
CREATE INDEX idx_roles_parent ON roles(parent_role_id) WHERE deleted = false;

COMMENT ON TABLE roles IS 'Role definitions for RBAC';
COMMENT ON COLUMN roles.parent_role_id IS 'Parent role for hierarchy (inherits permissions)';
COMMENT ON COLUMN roles.deleted IS 'Soft delete flag';

-- =============================================================================
-- Atomic permission definitions
-- =============================================================================
CREATE TABLE permissions (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    resource_type VARCHAR(50),
    action VARCHAR(50),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Soft delete fields
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_by VARCHAR(50)
);

CREATE INDEX idx_permissions_resource_type ON permissions(resource_type) WHERE deleted = false;

COMMENT ON TABLE permissions IS 'Atomic permission definitions';
COMMENT ON COLUMN permissions.id IS 'Permission ID in format resource:action (e.g., transactions:write)';
COMMENT ON COLUMN permissions.deleted IS 'Soft delete flag';

-- =============================================================================
-- Role to permission mappings (temporal - supports point-in-time queries)
-- =============================================================================
CREATE TABLE role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id VARCHAR(50) NOT NULL REFERENCES roles(id),
    permission_id VARCHAR(100) NOT NULL REFERENCES permissions(id),
    -- Audit fields (from AuditableEntity)
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Temporal fields for business audit trail
    granted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(50) REFERENCES users(id),
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by VARCHAR(50) REFERENCES users(id)
);

-- Index for active permissions lookup
CREATE INDEX idx_role_permissions_role_active ON role_permissions(role_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);
-- Unique constraint: only one active assignment per role-permission pair
CREATE UNIQUE INDEX role_permissions_active ON role_permissions(role_id, permission_id) WHERE revoked_at IS NULL;

COMMENT ON TABLE role_permissions IS 'Role to permission mappings with temporal audit trail';
COMMENT ON COLUMN role_permissions.revoked_at IS 'NULL means currently active';

-- =============================================================================
-- User role assignments (temporal - supports point-in-time queries)
-- =============================================================================
CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id),
    role_id VARCHAR(50) NOT NULL REFERENCES roles(id),
    organization_id VARCHAR(50),
    -- Audit fields (from AuditableEntity)
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Temporal fields for business audit trail
    granted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(50) REFERENCES users(id),
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by VARCHAR(50) REFERENCES users(id)
);

-- Index for active role lookup
CREATE INDEX idx_user_roles_user_active ON user_roles(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_user_roles_role ON user_roles(role_id);
CREATE INDEX idx_user_roles_org ON user_roles(organization_id) WHERE organization_id IS NOT NULL;
-- Unique constraint: only one active assignment per user-role-org combination
CREATE UNIQUE INDEX user_roles_active ON user_roles(user_id, role_id, COALESCE(organization_id, '')) WHERE revoked_at IS NULL;

COMMENT ON TABLE user_roles IS 'User to role assignments with temporal audit trail';
COMMENT ON COLUMN user_roles.organization_id IS 'Optional org scope for multi-tenancy';
COMMENT ON COLUMN user_roles.expires_at IS 'Optional expiration for temporary role assignments';
COMMENT ON COLUMN user_roles.revoked_at IS 'NULL means currently active';

-- =============================================================================
-- Instance-level permissions (user X can access resource Y) - temporal
-- =============================================================================
CREATE TABLE resource_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id),
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    permission VARCHAR(50) NOT NULL,
    -- Audit fields (from AuditableEntity)
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Temporal fields for business audit trail
    granted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(50) REFERENCES users(id),
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by VARCHAR(50) REFERENCES users(id),
    reason TEXT
);

-- Index for active permissions lookup
CREATE INDEX idx_resource_permissions_user_active ON resource_permissions(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_resource_permissions_resource ON resource_permissions(resource_type, resource_id);
-- Unique constraint: only one active permission per user-resource-permission combination
CREATE UNIQUE INDEX resource_permissions_active ON resource_permissions(user_id, resource_type, resource_id, permission) WHERE revoked_at IS NULL;

COMMENT ON TABLE resource_permissions IS 'Fine-grained permissions for specific resource instances with temporal audit trail';
COMMENT ON COLUMN resource_permissions.resource_type IS 'Type of resource (e.g., account, transaction)';
COMMENT ON COLUMN resource_permissions.resource_id IS 'ID of the specific resource instance';
COMMENT ON COLUMN resource_permissions.revoked_at IS 'NULL means currently active';

-- =============================================================================
-- User-to-user delegation
-- =============================================================================
CREATE TABLE delegations (
    id BIGSERIAL PRIMARY KEY,
    delegator_id VARCHAR(50) REFERENCES users(id) ON DELETE CASCADE,
    delegatee_id VARCHAR(50) REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_ids TEXT[],
    -- Audit fields (from AuditableEntity)
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    -- Temporal fields for business audit trail
    valid_from TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP(6) WITH TIME ZONE,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by VARCHAR(50) REFERENCES users(id),
    reason TEXT
);

CREATE INDEX idx_delegations_delegator ON delegations(delegator_id);
CREATE INDEX idx_delegations_delegatee ON delegations(delegatee_id);
CREATE INDEX idx_delegations_active ON delegations(delegatee_id)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE delegations IS 'User-to-user permission delegations';
COMMENT ON COLUMN delegations.scope IS 'Delegation scope: full, read_only, transactions_only';
COMMENT ON COLUMN delegations.resource_ids IS 'Specific resource IDs if not delegating all';

-- =============================================================================
-- Immutable authorization audit log
-- =============================================================================
CREATE TABLE authorization_audit_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    decision VARCHAR(20) NOT NULL,
    reason TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    additional_context JSONB
);

CREATE INDEX idx_audit_log_user ON authorization_audit_log(user_id);
CREATE INDEX idx_audit_log_timestamp ON authorization_audit_log(timestamp);
CREATE INDEX idx_audit_log_action ON authorization_audit_log(action);
CREATE INDEX idx_audit_log_decision ON authorization_audit_log(decision);

COMMENT ON TABLE authorization_audit_log IS 'Immutable audit trail for authorization events';
COMMENT ON COLUMN authorization_audit_log.decision IS 'GRANTED or DENIED';
COMMENT ON COLUMN authorization_audit_log.additional_context IS 'Extra context as JSON';
