# Implement runtime generation of Python bean metadata

## Objective and starting point

Generate Python bean definitions and introspections as JVM bytecode at application
runtime, using resolved metadata saved by the compiler. Preserve Micronaut behavior
and compile-time diagnostics. Keep build-time generation available.

Start from prototype commit `319c9055cb` on
`codex/python-runtime-metadata-prototype`, based on reviewed snapshot `c71342b8df`.
The prototype proves that actual metadata classes can be generated on demand,
loaded into the wrapper's classloader, and used by a running application without
javac or the Python compiler. It covers simple no-argument classes, singleton
resolution, scalar properties, concurrent lookup and separate classloaders.

The implementation must close these demonstrated gaps:

- Sourcegen still links AST API classes from `core-processor` at runtime.
- The descriptor captures declarations before visitors and cannot represent their
  metadata contributions or general annotation semantics.
- Definitions and introspections support only a small subset of their contracts.
- The introspection fallback supports known-class lookup, not enumeration.
- Incremental compilation, mode changes and output cleanup are not implemented.
- Build/startup/memory benefits have not been established.

## Target architecture

```text
Python + Java symbol analysis + annotation/type/bean visitors
                         |
             validated, resolved generation model
                         |
           +-------------+----------------+
           |                              |
   build-time backend             versioned model resources
           |                       + discovery catalogs
   metadata class files                    |
                                 lightweight runtime references
                                           |
                                 shared bytecode generation
                                           |
                                 class definition and caching
```

Compile time continues to resolve annotations, generics, constructors, injection
points and visitor transformations. Runtime performs context-dependent decisions
and emits classes from the saved model. It does not reparse Python, run annotation
processors, or rediscover application annotations through reflection.

Bean references must expose enough information for candidate selection before
loading a full definition. Introspection catalogs must likewise support discovery
without generating every introspection. Definitions may still be needed during
context startup; deferred generation does not imply that all generation waits for
an explicit `getBean` call.

Keep JVM wrappers, target-type mapping adapters and Java symbol lookup in the
initial implementation. Reducing those artifacts or removing javac from the build
is a separate follow-up. Retain build-time output for native-image deployments.

## Ordered implementation changes

### 1. Establish the parity harness and reproducible baseline

**Scope:** `inject-python-test`, compiler test fixtures and measurement tooling.

- Run the same fixtures in build-time and runtime modes; compare observable
  metadata, discovery results and application behavior.
- Preserve the separate-JVM test that excludes `jdk.compiler`, `inject-python`
  and `inject-java`. Add artifact assertions proving that selected definitions and
  introspections are absent from packaged output.
- Record exact commits, dependency versions, JDK/GraalPy configuration, output
  inventories and build/startup measurements. Measure fallback and JIT-enabled
  runtimes separately.
- Track the existing `ArgumentDef` compatibility failure against 5.2.2 separately.
  Do not suppress compatibility checks on existing modules to accommodate it.

**Acceptance:** reproducible prototype tests plus a baseline measurement report.
The prior 127 compiler and 912 integration tests are useful starting coverage;
they do not establish runtime-mode parity for the unsupported features.

### 2. Separate Sourcegen runtime generation from compiler APIs

**Scope:** Sourcegen repository, dependency catalog and `context-python-runtime`.

- Introduce a runtime-safe model and bytecode entry point with no dependency on
  Micronaut AST classes, `VisitorContext`, JavaParser or annotation processors.
- Move AST conversion into compiler-side adapters. Preserve existing processor
  APIs through compatible facades where possible.
- Audit method signatures, overloads, static initialization and bytecode helpers:
  changing a dependency to `compileOnly` is insufficient, as the prototype showed.
- Consume the updated Sourcegen version through the repository catalog. Remove
  `core-processor` from the runtime module's dependency graph.
- Document whether the standard `java.compiler` API module remains necessary;
  it is distinct from javac's `jdk.compiler` implementation.

**Acceptance:** the separate-JVM fixture still works without `core-processor`, AST
API classes, JavaParser, either Micronaut compiler, or `jdk.compiler` available.
Verify the resolved runtime dependency graph as well as class loading. Existing
Sourcegen compiler consumers and compatibility checks must continue to pass.

### 3. Define the resolved metadata format and generation boundary

**Scope:** compiler-side metadata analysis, `core-processor`, descriptor model/codec.

- Replace the prototype's handwritten properties subset with a versioned,
  deterministic format. Define schema/backend compatibility and diagnostics for
  unsupported, corrupt or mismatched models.
- Store resolved annotation values, defaults, stereotypes, aliases, repeatables,
  nested annotations, class-valued members and declaration/type-use distinctions.
  Preserve existing source-retention behavior; compile-time effects must survive
  without incorrectly exposing source-retained annotations at runtime.
- Include generic arguments, nullability, constructors, properties, method
  signatures, scope, qualifiers, conditions, injection/lifecycle plans, executable
  metadata and references to required generated helpers as features are enabled.
- Separate semantic analysis from output emission. Capture the model after the
  relevant visitor contributions have been applied, including associated beans
  and introspection/property transformations.
- Do not simply move the current descriptor writer below the visitor loop. Some
  visitors analyze and emit together; these phases need to be separated. The
  prototype already exposed automatically contributed introspection indexes.
- Use method-declaration metadata when identifying method annotations; Python
  method metadata can also include owning-class annotations.
- Keep file/source ownership and useful source locations in diagnostics. Do not
  serialize `ClassElement`, live compiler objects or pre-generated class bytes.

**Acceptance:** round-trip and differential tests preserve all enabled semantics,
including visitor-added metadata and compile-only annotation dependencies. A
selected unsupported construct fails compilation with a specific diagnostic.

### 4. Share bytecode construction and harden runtime loading

**Scope:** existing definition/introspection generation and the runtime backend.

- Extract generation from the resolved model so build-time and runtime backends
  share class construction logic. Avoid maintaining a second independent DI or
  introspection implementation based on the prototype's restricted base classes.
- Initially reproduce the prototype feature set through the shared backend.
  Retain the existing build-time path while moving individual generation features.
- Use recorded member descriptors for direct constructor/method/field bytecode;
  avoid runtime member scanning merely to reconstruct compiler-known signatures.
- Define deterministic names, loader/package placement and access requirements.
  Test inherited members, package access and named-module behavior before claiming
  support beyond ordinary classpath applications.
- Cache generated classes by type/classloader and model identity. Separate
  immutable generated artifacts from context-dependent definition state;
  conditions, evaluated expressions and qualifiers must not leak across contexts.
- Make concurrent first lookup and partial failures deterministic. Do not retry
  `defineClass` after it succeeded merely because subsequent initialization failed.
  Ensure caches do not retain discarded application classloaders.

**Acceptance:** both backends pass the initial parity fixtures. Concurrent lookup
produces one generated class per identity; separate loaders and contexts remain
independent. Generation errors identify the type, model and failing operation.

### 5. Integrate complete discovery

**Scope:** bean definition provider integration, core introspection discovery,
Python application bootstrap.

- Compose Python references with ordinary Java definitions and existing custom
  providers. Do not replace another provider's contribution or require users to
  manually wire the prototype provider into every application builder.
- Read discovery metadata without opening a Python context or generating classes.
  Resolve the bootstrap ordering explicitly to avoid DI/Python-context cycles.
- Define catalogs per compilation unit and deterministic merging across source
  roots and dependency JARs, including duplicate/conflicting model handling.
- Add an introspection catalog/provider integration for known-class lookup,
  `findIntrospections` and `findIntrospectedTypes`, preserving their filters and
  annotation metadata. Lightweight reference objects can be supplied by a shared
  provider; per-type generated reference classes should not be necessary.
- Preserve precedence for existing build-time definitions/introspections and make
  duplicate ownership an actionable error. For a claimed runtime model, generation
  failure must not be silently treated as an ordinary introspection lookup miss.

**Acceptance:** normal application startup discovers mixed build-time/runtime
artifacts; introspection lookup and enumeration agree. Catalog discovery alone
leaves generation counts at zero and does not initialize GraalPy.

### 6. Expand semantics in independently testable increments

**Scope:** resolved model, shared backend and existing Python integration fixtures.

| Increment | Coverage to implement | Required behavior checks |
| --- | --- | --- |
| 6a: DI and scope | Constructor/field/method injection, providers, optional dependencies, generics, inheritance, singleton/prototype/custom scopes, qualifiers and conditions | Same resolution, errors and scope behavior in both modes; context-dependent decisions remain isolated |
| 6b: Bean creation and lifecycle | Factories, associated beans, configuration binding, `EachBean`/`EachProperty`, lifecycle callbacks, disposal and event adapters | Correct metadata, initialization order, multiplicity, cleanup and failure handling |
| 6c: Introspection | Constructor selection, defaults, immutable/read-only/write-only properties, collections, nested/generic types, enums, property/member annotations and indexes | Same construction, mutation, reconstruction, validation and serialization behavior |
| 6d: Executable methods and AOP | Executable method metadata, interception, introduction, proxy targets, async methods and expression/helper dependencies | Same invocation and interception behavior with no missing generated dependencies |

Bean and introspection selection must become independent; a bean must not need
`@Introspected` merely to use runtime bean-definition generation.

AOP proxy and expression helper classes may remain build-time artifacts during
6d. Record this explicitly in the model and artifact inventory; deferring the
bean definition must not remove classes it still needs. Runtime generation of
those helpers is a separate decision, not an implicit claim of this change.

**Acceptance:** move each relevant fixture into the dual-mode matrix as its feature
lands. Keep unsupported features behind explicit diagnostics until their increment
passes; no silent fallback that conceals missing runtime functionality.

### 7. Implement incremental compilation and mode transitions

**Scope:** compiler output ownership, dependency tracking and catalogs.

- Track models, catalog fragments, bytecode, service entries and helper outputs
  against originating sources and their Java/Python dependencies.
- Update catalog entries atomically. Handle deletion, rename, changed annotations,
  changed Java signatures and removed generated helpers.
- On build-time/runtime mode changes, remove obsolete metadata classes or model
  resources and stale service registrations. A warm output directory must behave
  like a clean compilation.
- Preserve aggregating/isolating visitor invalidation rules and mixed Java/Python
  source-root behavior. Fingerprint schema/backend/compiler options in build state.
- Reject incompatible packaged models clearly. Application classes that have
  already been defined require a fresh classloader for regeneration; do not imply
  that incremental builds provide in-place JVM class replacement.

**Acceptance:** clean and incremental output inventories and runtime behavior match
across edit/delete/rename/mode-switch scenarios. No duplicate or stale registrations.

### 8. Qualify and release the opt-in mode

**Scope:** build configuration, documentation, compatibility and performance.

- Replace the prototype's comma-separated class option with documented generation
  mode/selection configuration. Keep the existing build-time mode as the default
  for the initial release and select it for native-image builds.
- Specify supported JVM, classloader and module configurations. Keep internal
  implementation types internal; review any new discovery/configuration extension
  as an additive experimental API and derive `@since` from the actual target release.
- Run affected module suites, the full dual-mode matrix, mixed Java/Python and
  third-party integration coverage, style checks, documentation and compatibility
  checks. Include relevant serialization, validation, HTTP and native-image tests.
- Compare corrected build-time and runtime baselines using repeated, alternating
  runs: build phases, packaged artifacts/dependencies, cold startup, first bean and
  introspection lookup, total runtime-generated classes, allocation and retained
  memory. Include applications that use few and many of their declared types.
- Record startup/first-use/memory budgets before tuning. Decide whether changing
  the default is justified only after behavioral parity and measured tradeoffs.

**Acceptance:** an opt-in release with documented limits, a straightforward return
to build-time generation, and a reproducible compatibility/performance report.
Changing the default is a later release decision.

## Dependencies and first delivery

Change 1 starts immediately. Changes 2 and 3 can be developed independently after
the baseline exists. Change 4 joins them; change 5 completes the first usable
integration. Each change in 6 can then land separately. Change 7 can start when
the format and catalog ownership are stable, but must cover every enabled feature
before change 8.

The first implementation batch is **1–4**, ending with the current small feature
set running on a compiler-free runtime backend and a shared generation model.
Its completion criteria are concrete: no `core-processor` at runtime, no selected
metadata class files in the build output, equivalent metadata/behavior in both
modes, and successful concurrent/separate-classloader tests.

No speedup is assumed. The earlier single diagnostic run attributed 1.43 seconds
of a 22.86-second compiler task to the entire Python bean-definition phase, not
just bytecode emission; introspection generation was not isolated. Moving emission
alone may save less while adding startup work. Python transformation/model parsing,
wrapper reduction and target-type mapping consolidation remain separate performance
workstreams.
