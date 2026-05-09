# Guide Inventory Snapshot

Snapshot generated from `micronaut-projects/micronaut-guides` `master` at commit `3e6d159` on 2026-05-06 during DEV-256 implementation.

This is a navigation aid, not a source of truth. Regenerate from current upstream before deciding that a topic is new.

## Snapshot Summary

- Guide directories under `guides/`: 185.
- `metadata.json` files under guide directories: 185.
- Common snippets live under `src/docs/common`.
- Metadata model: `buildSrc/src/main/java/io/micronaut/guides/core/Guide.java`.
- Category labels: `buildSrc/src/main/java/io/micronaut/guides/Category.java`.
- Dynamic tasks: guide slug lowerCamelCase plus `Build` or `RunTestScript`.

## Regenerate Inventory

From a fresh `micronaut-guides` checkout:

```bash
find guides -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort
jq -r '.categories[]? // .category? // empty' guides/*/metadata.json | sort | uniq -c | sort -nr
jq -r '.tags[]? // empty' guides/*/metadata.json | sort | uniq -c | sort -nr
rg -n '"publish": false|"base"' guides/*/metadata.json
```

If `jq` output is noisy because of shell or filter precedence, use `rg` for `base` and `publish` first, then inspect the individual metadata files.

## High-Signal Topic Families

Representative families present in the snapshot:

- Getting started and basics: `creating-your-first-micronaut-app`, `micronaut-configuration`, dependency injection, scopes, validation, records.
- HTTP and APIs: HTTP server, HTTP client, CORS, filters, content negotiation, XML, OpenAPI, JSON Schema, GraphQL, WebSocket.
- Testing: Rest Assured, MockServer, Testcontainers, Kafka listener testing, security testing.
- Data: JDBC, JPA, R2DBC, MongoDB, Flyway, Liquibase, jOOQ, many-to-many examples, cloud database variants.
- Security: basic auth, JWT, cookie JWT, session, API keys, X.509, OAuth2/OIDC providers, client credentials, token propagation.
- Cloud and serverless: AWS Lambda, Azure Functions, Google Cloud Run, Oracle Cloud, App Engine, cloud secrets, parameter stores, object storage.
- Messaging and distributed systems: Kafka, RabbitMQ, MQTT, JMS/SQS, service discovery, tracing, metrics.
- Distribution: executable JARs, Docker images, container registry pushes, GraalVM native image, CRaC, Kubernetes.
- Server-side HTML: Views, Thymeleaf, Turbo, Hotwire, static resources, WebJars Stimulus.
- Migration: Spring Boot to Micronaut guide series.
- AI and tools: MCP stdio/http examples, GraalPy.

## Top Categories in the Snapshot

Highest-count categories at the inspected revision:

- Micronaut Security: 12
- Authorization Code: 11
- Distribution: 10
- AWS Lambda: 10
- Distributed Tracing: 7
- Spring Boot, Service Discovery, Messaging, GraalVM, Data JDBC, Boot to Micronaut Building a REST API: 6 each
- Testing, Secrets Manager, Distributed Configuration, Data Access: 5 each
- Serverless, OpenAPI, MongoDB, MCP, Kubernetes, Email: 4 each

Refresh before relying on counts.

## Top Tags in the Snapshot

High-frequency tags included:

- `security`
- `oauth2`
- `oidc`
- `cloud`
- `spring-boot`
- `database`
- `validation`
- `oracle`
- `micronaut-data`
- `lambda`
- `service-discovery`
- `distributed-tracing`

Use tags for discovery and dedupe, but do not treat them as a substitute for reading guide bodies.

## Base or Partial Guides

The snapshot included these `publish: false` guide directories:

- `distribution-base`
- `hello-base`
- `micronaut-cloud-database-base`
- `micronaut-cloud-oidc-base`
- `micronaut-cloud-trace-base`
- `micronaut-data-many-to-many-base`
- `micronaut-email`
- `micronaut-mcp-diskspace`
- `micronaut-mcp-weather`
- `micronaut-object-storage-base`
- `micronaut-openapi-base`
- `micronaut-serverless-function-aws-lambda-request-stream-handler-base`

Common base consumers include cloud database, object storage, OpenAPI, MCP transport variants, email provider variants, and distribution examples. Inspect both the child guide and its base before editing either.

## Representative Guides to Inspect

Choose examples by similarity:

- First application: `creating-your-first-micronaut-app`.
- Simple HTTP/client source includes: `micronaut-http-client`.
- Configuration and snippet tags: `micronaut-configuration`.
- Testing style: `micronaut-rest-assured`, `testing-rest-api-integrations-using-mockserver`.
- Data JDBC and migrations: `micronaut-data-jdbc-repository`, `micronaut-flyway`, `working-with-jooq-flyway-using-testcontainers`.
- Security/OIDC: `micronaut-security-jwt`, `micronaut-oauth2-oidc-google`, `micronaut-oauth2-keycloak`.
- Base-guide reuse: `hello-base` plus `micronaut-cors` or `micronaut-health-endpoint`.
- Multi-app guide: distributed tracing or service discovery guides.
- Cloud guide: object storage or cloud database provider variants.
- OpenAPI: `micronaut-openapi-base`, `micronaut-openapi-generator-client`, `micronaut-openapi-generator-server`.
- MCP: `micronaut-mcp-weather`, `micronaut-mcp-weather-http`, `micronaut-mcp-weather-stdio`.

## Dedupe Decision

Before creating a new guide, record:

- Proposed slug.
- Existing guides with matching words, APIs, categories, tags, annotations, dependencies, or generated features.
- Whether related `publish: false` base guides exist.
- Why a new guide is warranted, or which existing guide will be updated instead.
