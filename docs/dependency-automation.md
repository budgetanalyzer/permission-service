# Dependency Automation

The workspace-wide operating policy, activation steps, cost boundary, and
failure triage are owned by
[orchestration's dependency automation guide](../../orchestration/docs/dependency-automation.md).
This document records only the `permission-service` integration and review
checks.

## Update discovery

`renovate.json` extends the shared Budget Analyzer preset from the
orchestration repository's default branch. Renovate's native Gradle, Gradle
Wrapper, Dockerfile, and GitHub Actions managers discover this repository's
version catalog, build script, wrapper distribution, base images, and workflow
actions. The Gradle catalog extraction includes the declared `serviceCommon`
version used by `spring-platform` and `service-web`.

`service-common` is hosted in GitHub Packages. Configure the Mend Renovate
Community App's supported encrypted Maven credentials and verify an
authenticated lookup; never put a package token in this repository or the
shared preset. Renovate proposes changes only to direct declarations. Versions
inherited from the Spring Boot and `spring-platform` BOMs remain represented by
the resolved dependency graph.

## Resolved dependency graph

`.github/workflows/dependency-submission.yml` generates and submits the Gradle
dependency graph on `main` pushes, weekly runs, and manual dispatches. The
official Gradle action uses its basic cache provider and resolves all projects
and resolvable configurations so application, runtime, build, and test
dependency trees are included. Do not add configuration filters without
proving equivalent coverage.

Remote package resolution uses `SERVICE_COMMON_PACKAGES_USERNAME` and
`SERVICE_COMMON_PACKAGES_READ_TOKEN`, exposed to Gradle as `GITHUB_ACTOR` and
`GITHUB_TOKEN`. These package-read credentials are distinct from
`${{ github.token }}`, which the action uses to submit the graph under the
job's only elevated permission, `contents: write`. Gradle resolution is
authoritative for both release and timestamped snapshot artifacts; do not add
manual artifact URL probes that duplicate Gradle's Maven metadata handling.
The graph snapshot is submitted directly and is not retained as an artifact or
published as a Build Scan.

The workflow must fail when package credentials are missing, either pinned
`service-common` artifact cannot be resolved, graph generation is incomplete,
or GitHub rejects submission. Do not substitute Maven Local, omit an internal
dependency, filter failed configurations, or treat graph-generation failure as
a clean security result.

## Build workflow and artifact retention

`.github/workflows/build.yml` runs on pushes and pull requests targeting
`main`, and on manual dispatch. It uses the normal Gradle Actions cache and the
same package-read secrets as local Gradle resolution. Regular CI uploads only
JUnit XML after a failed Gradle build and retains it for one day. Successful
builds retain no test-results artifact, and the workflow never uploads an
`app-jar` artifact.

Forked or otherwise untrusted pull requests do not receive package-read
secrets. A failure to resolve `service-common` in that context is an
unavailable-secret condition, not evidence that the dependency is absent. Do
not add `pull_request_target` or expose package credentials to untrusted
dependency branches to bypass that boundary.

## Bot pull request checks

Every Renovate pull request remains non-automerged. Review the resolved
dependency diff, release notes, Java 25 and Spring compatibility, and whether
the proposed direct dependency actually remediates an inherited alert. Major
updates remain visible in the Dependency Dashboard and require approval.

Run the required repository validation in order:

```bash
./gradlew clean spotlessApply
./gradlew clean build
```

Bot pull requests use the existing `build.yml` pull-request workflow. A
specific dependency compatibility failure is actionable; missing package
credentials from an untrusted context are not dependency-validation evidence.

## Hosted acceptance

After production promotion, verify that Renovate resolves the shared preset
from orchestration's default branch and can authenticate all
`org.budgetanalyzer` Maven lookups. A normal Renovate cycle must target `main`,
obey the shared concurrency and labeling policy, and keep automerge disabled.

Observe or manually dispatch one trusted dependency-submission run on `main`.
The accepted graph must include `spring-platform`, `service-web`, the Spring
Boot web, security, validation, and JPA stacks, Flyway and PostgreSQL, and the
H2, Testcontainers, WireMock, Spring Security, and JUnit test trees. Retain the
workflow URL and accepted GitHub dependency-graph snapshot as the hosted proof;
credential-free local graph generation cannot establish remote-resolution or
submission success.

Keep GitHub's dependency graph and Dependabot alerts enabled while overlapping
Dependabot version-update and security-update pull request creation remains
disabled. Use the resulting alerts only after GitHub has accepted the complete
graph.
