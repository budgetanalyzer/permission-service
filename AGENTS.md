# Permission Service

## Tree Position

**Archetype:** service
**Scope:** Budget Analyzer authorization data
**Role:** Owns RBAC data, including roles, permissions, and user-role assignments

### Relationships

- Consume shared Java patterns and runtime libraries from `../service-common/`.
- Supply user roles and permissions to Session Gateway during session creation and refresh.
- Let `../orchestration/` own deployment, routing, service-mesh policy, and full-stack local runtime wiring.
- Discover peer services with the commands in [Discovery](#discovery); do not maintain a peer inventory here.

### Boundaries

- Read this repository, `../service-common/`, and `../orchestration/docs/` when the task requires cross-repository context.
- Read `../ai-session-handler/docs/plan-format.md` when creating an implementation or execution plan for AI Session Handler.
- Write only within this repository.
- Do not modify sibling repositories from this context. Report any required cross-repository change to the user.

## Discovery

Use direct repository search and reads for code exploration. Never use agent or
subagent tools for code exploration.

```bash
# Repository structure
find . -maxdepth 2 -type f -not -path './.git/*' -not -path './build/*' | sort

# Source and test files
rg --files src/main src/test | sort

# Peer services
ls -d ../*-service

# Controllers, routes, and method security
rg -n '@(Get|Post|Put|Patch|Delete)Mapping|@RequestMapping|@PreAuthorize' \
  src/main/java --glob '*.java'

# Flyway migrations
find src/main/resources/db/migration -maxdepth 1 -type f | sort

# Build tasks and dependencies
./gradlew tasks
rg -n 'dependencies|implementation|testImplementation|runtimeOnly' build.gradle.kts
```

## Sources of Truth

- **Purpose, prerequisites, setup, and local use:** Read [README.md](README.md)
  before changing prerequisites, setup assumptions, or local run behavior.
- **Full-stack local environment:** Read
  [getting-started.md](../orchestration/docs/development/getting-started.md) before
  changing or debugging orchestration, Tilt, or workspace bootstrap behavior.
- **Runtime configuration:** Read
  [application.yml](src/main/resources/application.yml) before changing or
  documenting ports, context paths, database settings, logging, or Session
  Gateway revocation properties.
- **Authorization model:** Read
  [authorization-model.md](docs/authorization-model.md) before changing the
  RBAC schema, permission semantics, role composition, scoped permissions, or
  UI authorization guidance. Treat the complete ordered history under
  `src/main/resources/db/migration/` as the authority for current seeded data.
- **HTTP API and security annotations:** Read controller source under
  `src/main/java/org/budgetanalyzer/permission/api/` and
  `src/main/java/org/budgetanalyzer/permission/config/OpenApiConfig.java` before
  adding, removing, or reshaping endpoints or response contracts. Discover the
  current routes instead of copying an endpoint inventory into this file.
- **User deactivation and session revocation:** Read
  `src/main/java/org/budgetanalyzer/permission/service/UserService.java`,
  `src/main/java/org/budgetanalyzer/permission/client/SessionGatewayClient.java`,
  `src/main/java/org/budgetanalyzer/permission/config/SessionRevocationProperties.java`,
  and `src/main/resources/application.yml` before changing deactivation
  ordering, retry behavior, or failure semantics.
- **Build and dependencies:** Read `build.gradle.kts`, `settings.gradle.kts`,
  and `gradle/libs.versions.toml` before changing the toolchain, plugins,
  dependencies, test gates, or coverage gates.
- **Shared Spring architecture:** Read
  [spring-boot-conventions.md](../service-common/docs/spring-boot-conventions.md)
  when changing layers, entities, controllers, dependency injection, or HTTP
  response patterns. Read [service-common/AGENTS.md](../service-common/AGENTS.md)
  before implementing a new feature that uses shared architecture patterns.
- **Java quality:** Read
  [code-quality-standards.md](../service-common/docs/code-quality-standards.md)
  before writing or modifying Java code. Do not skip this prerequisite.
- **Errors and tests:** Read
  [error-handling.md](../service-common/docs/error-handling.md) when changing
  error flows or custom exceptions. Do not mock or spy application-owned
  Spring beans. Before writing or modifying tests, read
  [testing-patterns.md](../service-common/docs/testing-patterns.md) and follow
  its guidance for real components and concrete test implementations.
- **Session-edge and deployment architecture:** Read
  [system-overview.md](../orchestration/docs/architecture/system-overview.md),
  [session-edge-authorization-pattern.md](../orchestration/docs/architecture/session-edge-authorization-pattern.md),
  and [security-architecture.md](../orchestration/docs/architecture/security-architecture.md)
  before changing the internal permission-sync contract, claims flow, Session
  Gateway integration, or mesh security assumptions. Read
  [port-reference.md](../orchestration/docs/architecture/port-reference.md) for
  current exposure and caller rules.
- **CI and release artifact resolution:** Read
  [service-common-artifact-resolution.md](../orchestration/docs/development/service-common-artifact-resolution.md)
  when changing or debugging `service-common` resolution outside the normal
  local orchestration flow.

## Operating Rules

### Repository and Git Safety

- Never run git write operations such as `commit`, `push`, `checkout`, `reset`,
  branch manipulation, or history rewriting unless the user explicitly asks.
- Do not write outside this repository. Surface required sibling-repository
  changes instead of making them.
- Stop and report a missing prerequisite or an authority-boundary conflict. Do
  not invent a workaround.

### Java and Spring Architecture

- Follow the shared layered architecture: controllers own HTTP concerns,
  services own business rules and transactions, repositories own data access,
  and entities carry persistence state.
- Use `AuditableEntity` and `SoftDeletableEntity` according to the shared entity
  decision rules. Keep pure association rows simple when row existence is the
  business fact.
- Use the shared `ServletApiExceptionHandler` and exception hierarchy for
  consistent error responses.
- Name API models `*Request` and `*Response`; never use `*Dto` or `*DTO`.
- Use provider-independent identifiers in `{prefix}_{full-uuid-hex}` form.
- Import persistence APIs from `jakarta.persistence.*`; never use
  `org.hibernate.*` APIs.
- Apply the Java quality rules for `var`, full variable names, explicit imports,
  and Javadoc punctuation from the required code-quality document.

### Architectural Simplicity

- Choose the simplest implementation that correctly handles realistic inputs,
  states, and failure modes. Do not trade away security, data integrity, or
  required behavior for brevity.
- Put validation in the layer that owns the rule: request models and
  controllers validate request shape and syntax; services validate business
  invariants, ownership, persistence state, and cross-entity rules.
- Do not duplicate API validation in the service layer when every caller passes
  through the validated API contract. Validate again only when another caller
  can bypass that boundary or the service owns the rule.
- Do not add guards, fallbacks, custom exception paths, abstractions, or
  extension points for states that enforced boundaries make impossible.
- Handle plausible failures explicitly at external and asynchronous boundaries.
- Before adding a defensive branch, identify how the state can arise and what
  the caller or system can usefully do in response. Omit the branch if neither
  is concrete.

### Security

- Every controller endpoint method must have `@PreAuthorize`.
- The only exception is
  `InternalPermissionController#getUserPermissions`, which is protected by the
  narrow `/internal/v1/users/*/permissions` matcher in
  `PermissionServiceSecurityConfig`. Do not broaden this anonymous
  claims-header exception; orchestration must continue restricting callers
  through mesh identity and authorization policy.
- Use `SecurityContextUtil` to obtain the current user. Do not accept actor
  identity from a request body or untrusted header in application code.
- Do not bypass authentication, authorization, persistence, or validation
  layers as a durable fix.
- Preserve the deactivation contract: commit the local user-state change before
  external session revocation, keep transient retry bounded, and keep exhausted
  revocation safe for the caller to retry. Verify current details in the owner
  code and configuration before changing this flow.

### Roles and Permissions

- Manage roles, permissions, and role-permission mappings only through Flyway
  migrations. Do not add a runtime grant surface without an explicit design
  change.
- Use `{resource}:{action}` for own-resource permissions and
  `{resource}:{action}:any` for cross-user scope. Add a scoped permission only
  when a controller has a real cross-user access requirement; do not pre-create
  speculative variants.
- Preserve the grant-time action hierarchy within each scope:
  - Granting `{resource}:write` also requires `{resource}:read` on the same role.
  - Granting `{resource}:delete` also requires `{resource}:read` on the same role.
  - `:write` and `:delete` are independent; neither requires the other.
  - Before revoking `{resource}:read`, revoke matching `:write` and `:delete` grants.
  - Apply the same rules independently to `:any` permissions.
- Do not add runtime permission expansion. Downstream services and UIs rely on
  literal permission checks against well-formed migration grants.
- After any migration that adds, grants, or revokes a role permission, remind
  the user to synchronize
  `../service-common/service-web/src/main/java/org/budgetanalyzer/service/security/test/ClaimsHeaderTestBuilder.java`.
  This repository cannot make that required sibling-repository change.

## Development Workflow

1. Read the relevant source-of-truth documents before implementation.
2. Confirm the required Java toolchain, Gradle wrapper, database settings, and
   any external credentials described by `README.md` are available. Confirm
   Docker is available before running Testcontainers-backed integration tests.
3. Inspect the current source, tests, build configuration, and migrations with
   the discovery commands above.
4. Implement the smallest coherent change and update its owner documentation in
   the same work.
5. Run the validation gates appropriate to the changed files.
6. If a required tool, service, credential, container runtime, or verifier is
   unavailable, stop and report it. Do not claim full verification.

Use `./gradlew bootRun` for the service-only local entry point after satisfying
the prerequisites in `README.md`. Use the orchestration getting-started guide
for the supported full-stack path.

### AI Session Handler Plans

When creating an implementation or execution plan for AI Session Handler, read
and follow [plan-format.md](../ai-session-handler/docs/plan-format.md). Use its
canonical template, replace every placeholder, and retain numbered
`## Phase N: Title` headings.

Run a plan from this repository root with:

```bash
ai-session-handler run \
  --plan docs/plans/PLAN.md \
  --max-phases 999 \
  --quiet \
  --agent-cmd "../ai-session-handler/.venv/bin/ai-session-handler-codex-high --model MODEL"
```

Remove `--model MODEL` from the quoted agent command to use the wrapper's
configured or default model.

## Validation

Before completing Java, Gradle, or migration changes, run these commands in
sequence:

```bash
./gradlew clean spotlessApply
./gradlew clean build
```

- Inspect the full build output and fix Checkstyle warnings even if Gradle exits
  successfully.
- Use `./gradlew test --tests "FullyQualifiedTestClass"` for focused iteration;
  it does not replace the required full build.
- Ensure Docker is available when the affected test set includes
  Testcontainers-backed integration tests.
- For migration changes, inspect the complete ordered migration history and
  verify the role-permission action hierarchy in addition to running the build.
- For documentation-only changes, run `git diff --check`, verify every changed
  link target, and run or syntax-check changed commands. Do not run Gradle
  solely for Markdown changes.
- Never disable, weaken, or delete an existing test to make a change pass. If an
  unrelated test is already failing, stop and report it.
- If any required validation cannot run, state exactly what was not verified
  and why. Do not claim the work is fully verified.

## Documentation Maintenance

- Keep documentation current in the same work as configuration or code changes.
- Update `AGENTS.md` when instructions, guardrails, workflows, discovery
  commands, authority boundaries, or source-of-truth ownership changes. Before
  editing it, read and apply
  [agents-md-checkstyle.md](../orchestration/docs/agents-md-checkstyle.md).
- Update `README.md` when setup, usage, public purpose, or human onboarding
  changes.
- Update `docs/` when architecture, configuration behavior, APIs, operations,
  or design rationale changes.
- Do not update archived documents unless the user explicitly requests it.
- Keep detailed recurring topics in one owner document and link to it instead
  of copying the detail into `AGENTS.md`.
- Do not leave required documentation updates as follow-up work.

## Web Search Protocol

Before searching for current, latest, or best information, read the current
date from the runtime environment or conversation context and include the
current year in the query. Never infer the current year from model training
data. Use earlier years only when intentionally researching historical
information.
