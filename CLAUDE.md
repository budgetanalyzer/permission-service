# Permission Service

## Tree Position

**Archetype**: service
**Scope**: budgetanalyzer ecosystem
**Role**: Manages RBAC, resource permissions, delegations, and authorization audit logging

### Relationships
- **Consumes**: service-common (patterns)
- **Coordinated by**: orchestration
- **Peers with**: Discover via `ls /workspace/*-service`
- **Observed by**: architecture-conversations

### Permissions
- **Read**: `../service-common/`, `../orchestration/docs/`
- **Write**: This repository only

### Discovery
```bash
# My peers
ls -d /workspace/*-service
# My platform
ls ../service-common/
```

Authorization data management microservice for the Budget Analyzer application. Manages RBAC (Role-Based Access Control), resource-level permissions, user-to-user delegations, and authorization audit logging with full temporal tracking for compliance.

**Port:** 8086 | **Context Path:** `/permission-service` | **Database:** `permission`

## Project Status

This service is functionally complete for authorization data management. The unsolved problem is propagating user ownership to domain services (transaction-service, etc.) - that's the next architectural challenge, left as an exercise.

**Current focus:** Bug fixes and documentation, not new features.

See [orchestration docs](https://github.com/budgetanalyzer/orchestration/blob/main/docs/architecture/system-overview.md#intentional-boundaries) for the intentional boundary.

## Spring Boot Patterns

This service uses shared patterns from service-common. When implementing new features:

**Quick reference:**
- Extends `AuditableEntity` for audit fields (createdAt, updatedAt, createdBy, updatedBy)
- Extends `SoftDeletableEntity` for soft delete (deleted, deletedAt, deletedBy)
- Uses `GlobalExceptionHandler` for consistent error responses
- Uses `SecurityExceptionHandler` for auth error responses

**When to consult service-common documentation:**
- Adding new entities or modifying base entity behavior
- Changing exception handling patterns
- Understanding pagination or response envelope patterns

See [../service-common/README.md](../service-common/README.md)

## Service-Specific Patterns

### Role-Based Governance

Roles are tiered with assignment permission requirements:

| Tier | Roles | Required Permission |
|------|-------|---------------------|
| Basic | USER, ACCOUNTANT, AUDITOR | `user-roles:assign-basic` |
| Elevated | MANAGER, ORG_ADMIN | `user-roles:assign-elevated` |
| Protected | SYSTEM_ADMIN | Database-only (API blocked) |

Custom roles require `user-roles:assign-elevated` permission.

```bash
# Find governance logic
grep -r "assign-basic\|assign-elevated\|SYSTEM_ADMIN" src/main/java --include="*.java"
```

### Temporal Data Pattern

Assignment tables (UserRole, RolePermission, Delegation) use temporal fields for point-in-time queries:
- `granted_at` - When assignment became active
- `revoked_at` - When assignment was revoked (null if active)

This enables compliance queries like "What permissions did user X have on date Y?"

```bash
# Find temporal queries
grep -r "granted_at\|revoked_at\|pointInTime" src/main/java --include="*.java"
```

### Delegation System

User-to-user permission delegation with scope control:

| Scope | Description |
|-------|-------------|
| `full` | All permissions from delegator |
| `read_only` | Only read/list permissions |
| `transactions_only` | Only transaction resource access |

```bash
# Find delegation logic
grep -r "DelegationScope\|delegat" src/main/java --include="*.java"
```

### Permission Caching

Redis-based caching for performance:
- 5-minute TTL for cached permissions
- Pub/sub invalidation across instances
- Event-driven cache clearing on role/permission changes

See `PermissionCacheService` for implementation.

### Domain Model

**Core entities:**
- `User` - Local record linked to Auth0 via `auth0_sub`
- `Role` - Hierarchical RBAC with parent_role support
- `Permission` - Atomic permissions in `resource:action` format
- `UserRole` - Temporal user-role assignments
- `RolePermission` - Temporal role-permission mappings
- `ResourcePermission` - Instance-level permissions
- `Delegation` - User-to-user delegation with scope
- `AuthorizationAuditLog` - Immutable audit trail

```bash
# View domain model
tree src/main/java/org/budgetanalyzer/permission/domain
```

### Package Structure

```
org.budgetanalyzer.permission/
├── api/                    # REST controllers
│   ├── request/           # Request DTOs
│   └── response/          # Response DTOs
├── config/                # Configuration classes
├── domain/                # JPA entities
├── event/                 # Domain events
├── repository/            # JPA repositories
└── service/               # Business logic
    ├── dto/               # Service-layer DTOs
    └── exception/         # Custom exceptions
```

## API Documentation

**Swagger UI:** http://localhost:8086/permission-service/swagger-ui.html

**Key endpoint groups:**
- `/v1/users` - User permissions and role assignments
- `/v1/roles` - Role CRUD operations
- `/v1/delegations` - User-to-user delegations
- `/v1/resource-permissions` - Instance-level permissions
- `/v1/audit` - Authorization audit logs

**Via API Gateway:** http://localhost:8080/permission-service/...

```bash
# Find all endpoints
grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" src/main/java --include="*.java"
```

## Running Locally

**Prerequisites:**
- PostgreSQL with `permission` database
- Redis for permission caching
- Auth0 configuration (issuer URI, audience)
- Environment variables: `REDIS_HOST`, `REDIS_PORT`, `AUTH0_ISSUER_URI`, `AUTH0_AUDIENCE`

```bash
./gradlew bootRun
```

## Discovery Commands

```bash
# Find entities
ls src/main/java/org/budgetanalyzer/permission/domain/

# Find services
ls src/main/java/org/budgetanalyzer/permission/service/

# Find controllers
ls src/main/java/org/budgetanalyzer/permission/api/

# View migrations
ls src/main/resources/db/migration/

# Find security annotations
grep -r "@PreAuthorize" src/main/java --include="*.java"

# Find custom exceptions
ls src/main/java/org/budgetanalyzer/permission/service/exception/

# Find event handlers
grep -r "@EventListener\|ApplicationEvent" src/main/java --include="*.java"
```

## Build and Test

```bash
# Build and test
./gradlew clean build

# Format code (required before commit)
./gradlew clean spotlessApply

# Run tests only
./gradlew test
```

## Testing

**Patterns used:**
- JUnit 5 with Mockito
- `@ExtendWith(MockitoExtension.class)`
- Nested test classes with `@DisplayName`
- `TestConstants` for reusable test data
- ArgumentCaptor for verification
- AssertJ assertions

```bash
# Find test fixtures
ls src/test/java/org/budgetanalyzer/permission/

# Run specific test class
./gradlew test --tests "PermissionServiceTest"
```

## Notes for Claude Code

**Security requirements:**
- All controller methods MUST have `@PreAuthorize` annotations
- Use `SecurityContextUtil` to get current user
- Never expose SYSTEM_ADMIN assignment via API

**Role governance rules:**
- Check required permission tier before role assignment
- Throw `ProtectedRoleException` for SYSTEM_ADMIN operations
- Throw `DuplicateRoleAssignmentException` if role already assigned

**Temporal compliance:**
- Always populate `granted_at` on new assignments
- Set `revoked_at` instead of deleting for audit trail
- Support point-in-time queries for compliance

**Cache invalidation:**
- Invalidate cache on role assignment/revocation
- Invalidate cache on delegation create/revoke
- Use `PermissionCacheService.invalidateUserPermissions()`

**Code style:**
- Google Java Format enforced via Spotless
- Run `./gradlew spotlessApply` before committing

### Web Search Year Awareness

Claude's training data may default to an outdated year. When using WebSearch for best practices or current information:

1. Check `<env>Today's date</env>` for the actual current year
2. Include that year in searches (e.g., "Spring Boot best practices 2025" not 2024)
3. This ensures results reflect current standards, not outdated patterns

## Conversation Capture

When the user asks to save this conversation, write it to `/workspace/architecture-conversations/conversations/` following the format in INDEX.md.
