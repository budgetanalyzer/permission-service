# Permission Service

> "Archetype: service. Role: Manages RBAC and authorization data (roles, permissions, user-role assignments)."
>
> — [AGENTS.md](AGENTS.md#tree-position)

[![Build](https://github.com/budgetanalyzer/permission-service/actions/workflows/build.yml/badge.svg)](https://github.com/budgetanalyzer/permission-service/actions/workflows/build.yml)

Authorization data management microservice for the Budget Analyzer application. Manages clean RBAC with 2 default roles (ADMIN, USER), simple join tables for role-permission and user-role mappings, and an internal endpoint Session Gateway uses during login, token exchange, and heartbeat-driven refresh.

The internal permissions endpoint is a service-owned security exception: it does not require
claims headers in the application because Session Gateway calls it before user claims exist.
Caller restriction for that path is enforced by orchestration through mesh identity and
authorization policy, not by a shared `/internal/**` rule in `service-common`.

## Scope & Boundaries

**What this service does:**
- Manages authorization metadata: users, roles, permissions
- Provides RBAC with simple role-permission mappings
- Exposes an internal endpoint for Session Gateway to sync users and resolve roles/permissions before it writes the Redis session hash

**What this service does NOT solve:**
- Data ownership: "Which transactions belong to which user?"
- Cross-service user scoping
- Multi-tenancy / organization isolation

Data ownership is intentionally left as an exercise — see [orchestration docs](https://github.com/budgetanalyzer/orchestration/blob/main/docs/architecture/system-overview.md#intentional-boundaries) for why.

## Overview

The Permission Service manages authorization data including:
- Users (local records linked to identity provider via `idp_sub`)
- Roles (2 defaults: ADMIN, USER; custom roles supported)
- Permissions (atomic definitions in `resource:action` format)
- User-Role assignments (simple join table)
- Role-Permission mappings (simple join table)

The `idp_sub` field stores the OIDC `sub` claim from any compliant identity provider. This is intentionally provider-agnostic to avoid identity provider lock-in — the current deployment uses Auth0, but no Auth0-specific logic exists in the codebase.

## Configuration

| Property | Value |
|----------|-------|
| Port | 8086 |
| Context Path | `/permission-service` |
| Database | `permission` |

## Prerequisites

- Java 24+
- PostgreSQL database named `permission`
- OIDC identity provider configured (issuer URI, audience)

## Development

### Build

```bash
./gradlew build
```

### Run

```bash
cd ../orchestration
tilt up

cd ../permission-service
export SPRING_DATASOURCE_PASSWORD=your_permission_database_password
./gradlew bootRun
```

`SPRING_DATASOURCE_USERNAME` already defaults to `permission_service`, and the
host defaults to `localhost:5432`. If you are reusing values from
`../orchestration/.env`, map `POSTGRES_PERMISSION_SERVICE_PASSWORD` to
`SPRING_DATASOURCE_PASSWORD`.

### Test

```bash
./gradlew test
```

## Database

The service uses Flyway for database migrations. Migrations are located in:
- `src/main/resources/db/migration/`

### Schema

Key tables:
- `users` — Local user records linked to identity provider via `idp_sub`
- `roles` — Role definitions (soft-deletable)
- `permissions` — Atomic permission definitions in `resource:action` format
- `user_roles` — Simple user-role join table
- `role_permissions` — Simple role-permission join table

### Default Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| ADMIN | Full access | All 16 permissions |
| USER | Standard access | transactions:read/write, accounts:read/write, budgets:read/write |

Custom roles can be created via the API. Role assignment requires `roles:write` permission.

## API Endpoints

### User Permissions (`/v1/users`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/v1/users/me/permissions` | Get current user's effective permissions | Authenticated |
| GET | `/v1/users/{id}/permissions` | Get a user's effective permissions | `users:read` |
| GET | `/v1/users/{id}/roles` | Get a user's roles | `users:read` or own ID |
| POST | `/v1/users/{id}/roles` | Assign role to user | `roles:write` |
| DELETE | `/v1/users/{id}/roles/{roleId}` | Revoke role from user | `roles:write` |

### Roles (`/v1/roles`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/v1/roles` | List all active roles | `roles:read` |
| GET | `/v1/roles/{id}` | Get role by ID | `roles:read` |
| POST | `/v1/roles` | Create new role | `roles:write` |
| PUT | `/v1/roles/{id}` | Update role | `roles:write` |
| DELETE | `/v1/roles/{id}` | Soft-delete role | `roles:delete` |

### Internal (`/internal/v1/users`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/internal/v1/users/{idpSub}/permissions` | Sync user and return permissions for session creation, token exchange, and refresh | Service-owned path exception; ingress/mesh restricted |
| POST | `/internal/v1/users/{userId}/deactivate` | Commit deactivation and role removal, then attempt session revocation | Service-owned path exception; ingress/mesh restricted |

## Architecture

### Soft Delete Strategy

- **Users, Roles, Permissions**: Soft delete with `deleted` flag
- **Assignments (UserRole, RolePermission)**: Hard delete on revocation

### Deactivation Flow

- Deactivation commits the user status change and role removal in PostgreSQL before calling Session Gateway to revoke sessions
- The login and refresh gate uses an indexed `idp_sub + status` lookup so deactivated users are rejected without scanning the full `users` table

## Related Services

- **Session Gateway**: Session-based edge authorization service for browser clients; calls the internal endpoint to sync users and resolve roles/permissions before writing the Redis session hash
- **Transaction Service**: Transaction management
- **service-common**: Shared library (base entities, exception handling)

## License

Proprietary — Budget Analyzer
