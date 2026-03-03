-- =============================================================================
-- Users table (authorization subjects, linked to identity provider)
-- =============================================================================
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    idp_sub VARCHAR(255) NOT NULL,
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

CREATE INDEX idx_users_idp_sub ON users(idp_sub) WHERE deleted = false;
CREATE INDEX idx_users_email ON users(email) WHERE deleted = false;
-- Partial unique indexes to allow reuse after soft delete
CREATE UNIQUE INDEX users_idp_sub_active ON users(idp_sub) WHERE deleted = false;
CREATE UNIQUE INDEX users_email_active ON users(email) WHERE deleted = false;

COMMENT ON TABLE users IS 'Local user records linked to an identity provider for authorization';
COMMENT ON COLUMN users.id IS 'Internal user ID (e.g., usr_xxx)';
COMMENT ON COLUMN users.idp_sub IS 'Identity provider subject identifier (OIDC sub claim, provider-agnostic)';
COMMENT ON COLUMN users.deleted IS 'Soft delete flag';

-- =============================================================================
-- Role definitions
-- =============================================================================
CREATE TABLE roles (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
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

COMMENT ON TABLE roles IS 'Role definitions for RBAC';
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
-- Role to permission mappings (simple join table)
-- =============================================================================
CREATE TABLE role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id VARCHAR(50) NOT NULL REFERENCES roles(id),
    permission_id VARCHAR(100) NOT NULL REFERENCES permissions(id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE(role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS 'Role to permission mappings';

-- =============================================================================
-- User role assignments (simple join table)
-- =============================================================================
CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id),
    role_id VARCHAR(50) NOT NULL REFERENCES roles(id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE(user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

COMMENT ON TABLE user_roles IS 'User to role assignments';
