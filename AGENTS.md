# Permission Service

## Tree Position

**Archetype**: service
**Scope**: budgetanalyzer ecosystem
**Role**: Manages RBAC and authorization data (roles, permissions, user-role assignments)

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

## Code Exploration

NEVER use Agent/subagent tools for code exploration. Use Grep, Glob, and Read directly.

## Documentation Discipline

Always keep documentation up to date after any configuration or code change.

Update the nearest affected documentation in the same work:
- `AGENTS.md` when instructions, guardrails, discovery commands, or repository-specific workflow changes
- `README.md` when setup, usage, or repository purpose changes
- `docs/` when architecture, configuration, APIs, behaviors, or operational workflows change

Do not leave documentation updates as follow-up work.

Authorization data management microservice for the Budget Analyzer application. Manages clean RBAC with 2 default roles (ADMIN, USER), simple join tables for role-permission and user-role mappings, and an internal endpoint Session Gateway uses to sync users and resolve roles/permissions during session creation and refresh.

**Port:** 8086 | **Context Path:** `/permission-service` | **Database:** `permission`

## Project Status

This service provides clean RBAC for the Budget Analyzer ecosystem. Session Gateway integration is complete — Session Gateway calls the internal endpoint during login, token exchange, and heartbeat-driven refresh to sync users and resolve roles/permissions before it writes the Redis session hash. Envoy ext_authz later reads that session hash and injects claims headers into upstream requests.

**Current focus:** Bug fixes and documentation, not new features.

See [orchestration docs](https://github.com/budgetanalyzer/orchestration/blob/main/docs/architecture/system-overview.md#intentional-boundaries) for the intentional boundary.

## Coding Standards

**Before writing or modifying any Java code, read [code-quality-standards.md](../service-common/docs/code-quality-standards.md).** Do not skip this step. The most common violations: missing `var`, wildcard imports, abbreviated variable names, Javadoc without trailing periods.

## Spring Boot Patterns

**This service follows standard Budget Analyzer Spring Boot conventions.**

**Quick reference:**
- Extends `AuditableEntity` for audit fields (createdAt, updatedAt, createdBy, updatedBy)
- Extends `SoftDeletableEntity` for soft delete (deleted, deletedAt, deletedBy)
- Uses `ServletApiExceptionHandler` (from service-common) for consistent error responses including security exceptions
- DTOs: `*Request`, `*Response` — NEVER `*Dto`/`*DTO`
- Identifiers: `{prefix}_{full-uuid-hex}` — see service-common Vendor Independence
- Imports: Use `jakarta.persistence.*` — NEVER `org.hibernate.*`

**When to consult service-common documentation:**
- **Implementing new features** → Read [service-common/AGENTS.md](../service-common/AGENTS.md) for architecture patterns
- **Handling errors** → Read [error-handling.md](../service-common/docs/error-handling.md) for exception hierarchy
- **Writing tests** → Read [testing-patterns.md](../service-common/docs/testing-patterns.md) for JUnit 5 + TestContainers conventions
- **Code quality issues** → Read [code-quality-standards.md](../service-common/docs/code-quality-standards.md) for Spotless, Checkstyle, var usage

## Service-Specific Patterns

### Role Model

Two default roles seeded via migration:

| Role | Description | Permissions |
|------|-------------|-------------|
| ADMIN | Full access | All 19 permissions |
| USER | Standard access | transactions:read/write, accounts:read/write, budgets:read/write, statementformats:read |

Roles are managed exclusively via Flyway migrations, not at runtime.

### Domain Model

**Core entities (5 tables):**
- `User` - Local record linked to identity provider via `idp_sub`
- `Role` - Role definitions (soft-deletable)
- `Permission` - Atomic permissions in `resource:action` format
- `UserRole` - Simple user-role join table
- `RolePermission` - Simple role-permission join table

```bash
# View domain model
tree src/main/java/org/budgetanalyzer/permission/domain
```

### Internal Permissions Endpoint

`GET /internal/v1/users/{idpSub}/permissions` — Called by Session Gateway during login, token exchange, and heartbeat-driven refresh to:
1. Sync user from identity provider data (creates on first login)
2. Return `{ userId, roles, permissions }` for claims injection
3. Bypass claims-header auth only for this narrow path (`/internal/v1/users/*/permissions`) via `PermissionServiceSecurityConfig`; orchestration still restricts callers with mesh identity and authorization policy

### User Deactivation Endpoint

`POST /v1/users/{id}/deactivate` — Admin UI action on `UserPermissionController`, protected by `@PreAuthorize("hasAuthority('users:write')")`. The actor identity comes from the security context (no request body).

**Response semantics:**
- **200** — user deactivated and sessions revoked (returns `UserDeactivationResponse`)
- **503** — user deactivated but session revocation failed after bounded retry; safe to retry the same request

Session revocation uses bounded retry with exponential backoff configured via `session-gateway.revocation.*` properties in `application.yml`. The `SessionGatewayClient` retries connection failures, 5xx, and 429 responses; 4xx (except 429) fail immediately without retry.

### Package Structure

```
org.budgetanalyzer.permission/
├── api/                    # REST controllers
│   └── response/          # Response DTOs
├── client/                # Outbound HTTP clients
├── config/                # Configuration classes
├── domain/                # JPA entities
├── repository/            # JPA repositories
└── service/               # Business logic
    ├── dto/               # Service-layer DTOs
    └── exception/         # Custom exceptions
```

## API Documentation

**Swagger UI:** http://localhost:8086/permission-service/swagger-ui.html

**Endpoints (2 total):**
- `POST /v1/users/{id}/deactivate` - User deactivation (admin)
- `GET /internal/v1/users/{idpSub}/permissions` - Internal endpoint for Session Gateway user sync and claims lookup

**Via API Gateway:** http://localhost:8080/permission-service/...

```bash
# Find all endpoints
grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" src/main/java --include="*.java"
```

## Running Locally

**Prerequisites:**
- PostgreSQL with `permission` database

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

Repository integration tests use `PostgreSQLContainer`, so Docker must be available when running
the full test suite.

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

## NOTES FOR AI AGENTS

**Security requirements:**
- All controller methods MUST have `@PreAuthorize` annotations
- Exception: `InternalPermissionController#getUserPermissions` is protected by the narrow path rule (`/internal/v1/users/*/permissions`) in `PermissionServiceSecurityConfig` instead of method-level claims auth
- Use `SecurityContextUtil` to get current user

**Adding new permissions:**
- When adding new permissions via migration, also update `ClaimsHeaderTestBuilder` in `../service-common/service-web/src/main/java/org/budgetanalyzer/service/security/test/ClaimsHeaderTestBuilder.java` — add the new permission strings to the `ADMIN_PERMISSIONS` list and the `admin()` factory method so integration tests across all services reflect the correct admin claims shape.

**Code style:**
- Google Java Format enforced via Spotless
- Run `./gradlew spotlessApply` before committing

**NO GIT WRITE OPERATIONS**: Never run git commands (commit, push, checkout, reset, etc.) without explicit user request. The user controls git workflow entirely. You may suggest what to commit, but don't do it.

## Web Search Protocol

BEFORE any WebSearch tool call:
1. Read `Today's date` from `<env>` block
2. Extract the current year
3. Use current year in queries about "latest", "best", "current" topics
4. NEVER use previous years unless explicitly searching historical content

FAILURE MODE: Training data defaults to 2023/2024. Override with `<env>` year.

## Conversation Capture

When the user asks to save this conversation, write it to `/workspace/architecture-conversations/conversations/` following the format in INDEX.md.

---

## External Links (GitHub Web Viewing)

*The relative paths in this document are optimized for Claude Code. When viewing on GitHub, use these links to access other repositories:*

- [Service-Common Repository](https://github.com/budgetanalyzer/service-common)
- [Service-Common AGENTS.md](https://github.com/budgetanalyzer/service-common/blob/main/AGENTS.md)
- [Error Handling Documentation](https://github.com/budgetanalyzer/service-common/blob/main/docs/error-handling.md)
- [Testing Patterns Documentation](https://github.com/budgetanalyzer/service-common/blob/main/docs/testing-patterns.md)
- [Code Quality Standards](https://github.com/budgetanalyzer/service-common/blob/main/docs/code-quality-standards.md)
- [Orchestration Repository](https://github.com/budgetanalyzer/orchestration)
- [Orchestration AGENTS.md](https://github.com/budgetanalyzer/orchestration/blob/main/AGENTS.md)
