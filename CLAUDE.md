# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sunrise Letting is a property letting website. Visitors browse and search properties, request viewings and apply; Property Managers log in to a separate area to manage listings. The product brief in `docs/brief.md` is the source of truth for scope, including what is explicitly out of scope.

The services are written in Java with Spring Boot, built with Maven and tested with JUnit 5. There is no application code yet.

## Service layout and commands

Each domain service is its own Spring Boot application, as a Maven module under `services/<service>/` (e.g. `services/properties/`), so it can be deployed independently.

The root `pom.xml` is both the aggregator and the parent of every service:
- It inherits from `spring-boot-starter-parent`, which fixes the Spring Boot version and the versions of everything Spring Boot manages (Spring, JUnit 5, etc.). Java version is set there too.
- Versions for any other dependency go in its `<dependencyManagement>`. Service POMs declare dependencies **without versions**.
- A new service must be added to `<modules>` and use the root POM as its `<parent>`.

There is one Maven wrapper, at the root. Run commands from the root (use `mvnw.cmd` on Windows):

```
./mvnw verify                                                  # build and test all services
./mvnw verify -pl services/properties -am                      # build and test one service
./mvnw test -pl services/properties -Dtest=PropertySearchTest  # one test class
./mvnw test -pl services/properties -Dtest=PropertySearchTest#methodName  # one test method
./mvnw spring-boot:run -pl services/properties                 # run one service locally
```

## Development approach

The team works in an agile way: each issue is a small, independently deliverable user story, and only what the issue asks for gets built.

Development is test-driven. For each acceptance criterion:
1. Write a test that expresses the criterion and run it to see it fail.
2. Write the minimum code to make it pass.
3. Refactor with the tests green.

Production code without a test that drove it should not be added.

## Architecture constraints

- Runs on Kubernetes with **one service per domain area** (e.g. `identity`, `properties`, `viewings`, `applications`).
- A service must not reach into another service's data store; cross-service needs go through that service's interface.

## Front end

- The website is server-rendered with **Thymeleaf**, using **HTMX** for partial page updates (e.g. live search results, inline form validation). There is no single-page-app framework and no Node toolchain.
- All pages live in a dedicated **`web`** service (`services/web/`), for both the public Visitor pages and the Property Manager area. It owns templates, static assets and page controllers, and calls the domain services' APIs. It has no data store of its own and holds no domain logic.
- Domain services expose APIs only and never render HTML.
- HTMX endpoints return Thymeleaf fragments, not JSON.
- HTMX is delivered as a WebJar (`org.webjars.npm:htmx.org`), with its version in the root `<dependencyManagement>`. Add `org.webjars:webjars-locator-lite` (version managed by Spring Boot) so templates can reference it without a version, e.g. `/webjars/htmx.org/dist/htmx.min.js`. Don't copy front-end libraries into `static/` or load them from a CDN.
- Pages are test-driven like everything else: use MockMvc to assert on status, model and rendered HTML (including fragments returned to HTMX requests), with the domain service clients mocked.

## Workflow

All development is driven from GitHub issues on `kswkev/sunrise_letting`.

Branching:
- Each issue is worked on in its own feature branch, created from `develop` and named after the issue: `feature/<issue-number>-<short-kebab-title>` (e.g. `feature/4-search-properties-for-let`).
- Every commit on a feature branch starts with the issue number: `#<issue-number> <message>` (e.g. `#4 Add property search endpoint`).
- When the feature is complete, it is merged back into `develop` via a PR. Feature branches never target `main` directly.

Issues use `.github/ISSUE_TEMPLATE/feature.md` (user story, Given/When/Then acceptance criteria, affected services, out of scope, open questions). Every user story issue gets the `enhancement` label.

The subagents in `.claude/agents/` form the pipeline:

1. **business-analyst**: creates issues from the brief, or turns an existing issue into an implementation plan grouped by service. Never changes code.
2. **ui-ux-designer**: for issues with pages, designs the flow, layout, HTMX interactions and accessibility, writing a spec to `docs/design/<issue-number>-<title>.md`. Owns the site-wide design system in `docs/design/design-system.md`. Only writes under `docs/design/`.
3. **developer**: implements the plan steps for one service only, with tests. In the `web` service it builds pages to the issue's design spec. Does not commit, push or open PRs unless told to.
4. **tech-lead**: reviews the feature branch's diff against `develop` and the issue's acceptance criteria. Read-only.

## GitHub MCP

The GitHub MCP server is configured in `.mcp.json` and reads its token from the `GITHUB_TOKEN` environment variable. Subagents only get MCP tools listed by name in their `tools:` frontmatter, so when giving an agent new GitHub abilities, add the `mcp__github__*` tool there and, if it should run without prompting, to `permissions.allow` in `.claude/settings.local.json`. Changes to agent definitions only take effect after restarting Claude Code.
