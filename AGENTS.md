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

Authorization data management microservice for the Budget Analyzer application. Manages clean RBAC with 2 default roles (ADMIN, USER), simple join tables for role-permission and user-role mappings, and an internal endpoint for gateway claims resolution.

**Port:** 8086 | **Context Path:** `/permission-service` | **Database:** `permission`

## Project Status

This service provides clean RBAC for the Budget Analyzer ecosystem. Session-gateway integration is complete — the gateway calls the internal endpoint during ext_authz to resolve user claims (permissions, roles) which are injected as headers into upstream requests.

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

Custom roles can be created via the API. Role assignment requires `roles:write` permission.

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

`GET /internal/v1/users/{idpSub}/permissions` — Called by the session-gateway to:
1. Sync user from identity provider data (creates on first login)
2. Return `{ userId, roles, permissions }` for claims injection

### Package Structure

```
org.budgetanalyzer.permission/
├── api/                    # REST controllers
│   ├── request/           # Request DTOs
│   └── response/          # Response DTOs
├── config/                # Configuration classes
├── domain/                # JPA entities
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
- `/internal/v1/users` - Internal endpoint for gateway integration

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
- Use `SecurityContextUtil` to get current user

**Adding new permissions:**
- When adding new permissions via migration, also update `ClaimsHeaderTestBuilder` in `../service-common/service-web/src/main/java/org/budgetanalyzer/service/security/test/ClaimsHeaderTestBuilder.java` — add the new permission strings to the `ADMIN_PERMISSIONS` list and the `admin()` factory method so integration tests across all services reflect the correct admin claims shape.

**Role assignment:**
- Validate user and role exist before assignment
- Throw `DuplicateRoleAssignmentException` if role already assigned
- Hard delete on revocation (no temporal tracking)

**Code style:**
- Google Java Format enforced via Spotless
- Run `./gradlew spotlessApply` before committing

## Web Search Protocol

BEFORE any WebSearch tool call:
1. Read `Today's date` from `<env>` block
2. Extract the current year
3. Use current year in queries about "latest", "best", "current" topics
4. NEVER use previous years unless explicitly searching historical content

FAILURE MODE: Training data defaults to 2023/2024. Override with `<env>` year.

## Conversation Capture

When the user asks to save this conversation, write it to `/workspace/architecture-conversations/conversations/` following the format in INDEX.md.
