# Permission Service

> "Archetype: service. Role: Manages RBAC and authorization data (roles, permissions, user-role assignments)."
>
> — [AGENTS.md](AGENTS.md#tree-position)

[![Build](https://github.com/budgetanalyzer/permission-service/actions/workflows/build.yml/badge.svg)](https://github.com/budgetanalyzer/permission-service/actions/workflows/build.yml)

Authorization data management microservice for the Budget Analyzer application. Manages clean RBAC with 2 default roles (ADMIN, USER), simple join tables for role-permission and user-role mappings, and an internal endpoint [Session Gateway](https://github.com/budgetanalyzer/session-gateway) uses during login, token exchange, and heartbeat-driven refresh.

## Configuration

| Property | Value |
|----------|-------|
| Port | 8086 |
| Context Path | `/permission-service` |
| Database | `permission` |

## Prerequisites

- Java 25
- PostgreSQL database named `permission`
- OIDC identity provider configured (issuer URI, audience)

**Local development setup**: See [getting-started.md](https://github.com/budgetanalyzer/orchestration/blob/main/docs/development/getting-started.md)

**Service-common artifact resolution**: Local builds resolve `service-common`
from `mavenLocal()` — no GitHub credentials required. Default GitHub Actions
`build.yml` runs and release builds resolve the pinned `serviceCommon` version
from GitHub Packages. The full contract is documented in orchestration:
[service-common artifact resolution](https://github.com/budgetanalyzer/orchestration/blob/main/docs/development/service-common-artifact-resolution.md).
This service imports `org.budgetanalyzer:spring-platform` for shared Spring
dependency management and keeps `org.budgetanalyzer:service-web` explicit for
runtime utilities.

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

Repository integration tests use Testcontainers-backed PostgreSQL, so Docker must be available
when running the full test suite. `UserRoleRepositoryIntegrationTest` and
`UserIdentityIntegrationTest` both run Flyway migrations with Hibernate schema validation enabled
against PostgreSQL rather than the shared H2 test setup.

## Database

The service uses Flyway for database migrations. Migrations are located in:
- `src/main/resources/db/migration/`

### Default Roles

Two default roles (ADMIN, USER) are managed exclusively via Flyway migrations, not at runtime. See [docs/authorization-model.md](docs/authorization-model.md) for the full permission matrix, scoped permissions, and action hierarchy rationale.

## Capabilities

**User administration** — Search and view users with their role assignments. Deactivate users, which removes their roles and revokes active sessions.

**Permission resolution** — Internal endpoint that syncs a user from the identity provider and returns their resolved permissions. Called by Session Gateway during login, token exchange, and session refresh.

## Architecture

See [docs/authorization-model.md](docs/authorization-model.md) for the role/permission data model, rationale, and UI authorization guidance.

## Related Repositories

- [session-gateway](https://github.com/budgetanalyzer/session-gateway) — Edge authorization; calls the internal endpoint to sync users and resolve permissions
- [transaction-service](https://github.com/budgetanalyzer/transaction-service) — Transaction management
- [currency-service](https://github.com/budgetanalyzer/currency-service) — Currency management
- [service-common](https://github.com/budgetanalyzer/service-common) — Shared Spring platform and runtime libraries (base entities, exception handling)
- [orchestration](https://github.com/budgetanalyzer/orchestration) — Infrastructure and local dev environment

## License

Proprietary — Budget Analyzer
