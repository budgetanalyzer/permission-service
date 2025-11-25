# Permission Service

[![Build](https://github.com/budgetanalyzer/permission-service/actions/workflows/build.yml/badge.svg)](https://github.com/budgetanalyzer/permission-service/actions/workflows/build.yml)

Authorization data management microservice for the Budget Analyzer application.

## Scope & Boundaries

**What this service does:**
- Manages authorization metadata: users, roles, permissions, delegations
- Provides RBAC with hierarchical roles
- Tracks temporal data for compliance (who had what permission when)
- Maintains immutable audit logs

**What this service does NOT solve:**
- Data ownership: "Which transactions belong to which user?"
- Cross-service user scoping
- Multi-tenancy / organization isolation

The schema includes `organization_id` for future multi-tenancy, but this is not implemented. Data ownership is intentionally left as an exercise - see [orchestration docs](https://github.com/budgetanalyzer/orchestration/blob/main/docs/architecture/system-overview.md#intentional-boundaries) for why.

## Overview

The Permission Service manages authorization data including:
- Users (local records linked to Auth0)
- Roles (hierarchical RBAC)
- Permissions (atomic permission definitions)
- User-Role assignments
- Role-Permission mappings
- Resource-level permissions
- Delegations (user-to-user)
- Authorization audit logs

## Configuration

| Property | Value |
|----------|-------|
| Port | 8086 |
| Context Path | `/permission-service` |
| Database | `permission` |

## Prerequisites

- Java 24+
- PostgreSQL database named `permission`
- Redis (for caching)
- Auth0 tenant configured

## Development

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew bootRun
```

### Test

```bash
./gradlew test
```

## Database

The service uses Flyway for database migrations. Migrations are located in:
- `src/main/resources/db/migration/`

### Schema

Key tables:
- `users` - Local user records linked to Auth0
- `roles` - Role definitions with hierarchy support
- `permissions` - Atomic permission definitions
- `user_roles` - User-role assignments (temporal)
- `role_permissions` - Role-permission mappings (temporal)
- `resource_permissions` - Instance-level permissions (temporal)
- `delegations` - User-to-user delegations
- `authorization_audit_log` - Immutable audit trail

### Default Roles

| Role | Description |
|------|-------------|
| SYSTEM_ADMIN | Platform administration (database-only assignment) |
| ORG_ADMIN | Organization administration |
| MANAGER | Team oversight and approvals |
| ACCOUNTANT | Professional access to delegated accounts |
| AUDITOR | Read-only compliance access |
| USER | Self-service access to own resources |

## API Endpoints

(To be implemented in Phase 3+)

- `GET /me/permissions` - Get current user's effective permissions
- `GET /users/{id}/roles` - Get user's roles
- `POST /users/{id}/roles` - Assign role to user
- `DELETE /users/{id}/roles/{roleId}` - Revoke role from user
- `GET /roles` - List all roles
- `GET /permissions` - List all permissions

## Architecture

### Soft Delete Strategy

- **Users, Roles, Permissions**: Soft delete with `deleted` flag
- **Assignments (UserRole, RolePermission, etc.)**: Temporal with `granted_at`/`revoked_at`
- **Audit logs**: Immutable, never deleted

### Temporal Fields

Assignment tables support point-in-time queries:
```sql
-- What roles did user have on March 15th?
SELECT r.name FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
WHERE ur.user_id = ?
  AND ur.granted_at <= '2024-03-15'
  AND (ur.revoked_at IS NULL OR ur.revoked_at > '2024-03-15')
```

## Related Services

- **Session Gateway**: Browser authentication
- **Transaction Service**: Transaction management
- **service-common**: Shared library

## License

Proprietary - Budget Analyzer
