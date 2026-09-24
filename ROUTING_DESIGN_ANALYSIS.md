# Routing, filtering and route execution in Micronaut 5.3: design and performance analysis

Scope: the `router`, `http` (filter, bind, uri), `http-server` and `http-server-netty`
modules on `5.3.x`, compared with the `origin/functional-routes` branch. The goal is to
identify design and performance improvements for 5.3 / 5.4 and a path to fold the
functional-routes work into the mainline incrementally.

All file references are relative to the repository root and refer to `5.3.x` unless the
section says otherwise.

---

## 1. Executive summary

The server request path is already heavily optimised in places (sorted route arrays,
sync fast paths on `ExecutionFlow`, memoised filter lists, header caches), but the
architecture still carries three generations of design on top of each other:

1. A **mutable build-time route model** (`RouteBuilder` / `DefaultRouteBuilder` / `Route`,
   `UriRoute`, `ResourceRoute`) that dates from 1.0, converted at startup into
2. an **immutable runtime model** (`RouteInfo` / `UriRouteInfo` / `DefaultUrlRouteInfo`)
   added in 4.0, which is matched into
3. a **per-request `RouteMatch`** that mixes "what matched" (URI variables) with "binding
   progress" (argument values, pending binders, state flags) and is then handed around as a
   request attribute to filters, binders and the executor.

Consequences, in order of impact:

- **Matching is a linear scan per HTTP method with per-candidate allocation.** Every
  request tries every route of its method with prefix/`substring` matching, materialises a
  full `RouteMatch` (object plus four arrays plus a `LinkedHashMap`) for every route that
  matches, and only then resolves ambiguity. Ambiguity resolution re-derives template
  statistics with `Stream` pipelines on each call. Requests that miss (404/405/415/406)
  scan the whole table of all methods and allocate matches they never use.
- **Two URI template implementations are kept per route** (`UriMatchTemplate` with a
  compiled regex, and `UriTemplateMatcher`), doubling startup parsing and memory; only
  the second one is used to match.
- **Route selection and route execution are spread over four collaborating objects**
  (`DefaultRouter`, `RequestLifecycle`, `RouteExecutor`, `AbstractRouteMatch`) with
  overlapping responsibilities and a naming scheme (`fulfillBeforeFilters` /
  `fulfillAfterFilters`) that no longer describes when things happen.
- **Per-request state is stored in the attribute map** (`ROUTE_MATCH`, `ROUTE_INFO`,
  `URI_TEMPLATE`, plus response-side copies), which costs hash-map traffic and `Optional`
  wrappers on every access and forces the Netty request to special-case keys on cleanup.
- **The executor re-decides the response strategy on every call** with a cascade of
  `instanceof` checks, and the reactive paths allocate a fresh Reactor `Scheduler` and
  re-run executor selection per streaming response.
- **The public API surface is large and Stream/Optional based** (`Router`, `RouteBuilder`,
  `RouteMatch`), which pins the internals: `FilteredRouter` (versioning) has to go through
  the `Stream` path on every request.

The `functional-routes` branch replaces (1) and (2) with an immutable, indexed route table
fed by pluggable `RouteSource`s, adds a code-first `HttpRoutes` builder, and reworks the
filter pipeline so filters can replace the request URI/body before matching. It is a very
large change (roughly 40k added lines across 277 files in the routing commits alone). The
recommendation in section 8 is to land it in slices: the immutable route table and index
first (pure internal refactor with the biggest performance payoff), then the `RouteSource`
SPI, then the `HttpRoutes` builder behind an experimental annotation, and the filter
changes last.

---

## 2. Route objects: the three models

### 2.1 Build-time model (mutable)

| Type | Where | Notes |
|------|-------|-------|
| `RouteBuilder` | `router/.../RouteBuilder.java` (1361 lines) | 9 verbs × 3 overloads plus `resources`, `single`, `status`, `error`, naming strategy |
| `DefaultRouteBuilder.AbstractRoute` | `router/.../DefaultRouteBuilder.java:476` | mutable `conditions`, `consumesMediaTypes`, `producesMediaTypes`, `bodyArgument` |
| `DefaultUriRoute` | `DefaultRouteBuilder.java:791` | owns a `UriMatchTemplate`, `nestedRoutes`, `port`, `implicitHead`, a per-route `RouteExecutorSelector` |
| `DefaultResourceRoute` / `DefaultSingleRoute` | `DefaultRouteBuilder.java:1032-1247` | build 4-5 routes then `handleExclude` removes some from the builder list again |
| `AnnotatedMethodRouteBuilder` | `router/.../AnnotatedMethodRouteBuilder.java` | one lambda per HTTP annotation, all doing the same thing with copy-pasted bodies |

Observations:

- `nest(Runnable)` works by mutating a builder-level field (`currentParentRoute`,
  `DefaultRouteBuilder.java:93,973-982`), so nesting is only correct on a single thread and
  only while the builder is being filled.
- `exposedPort(int)` (`DefaultRouteBuilder.java:945-950`) both records the port and adds a
  `where` predicate for it. At request time `DefaultRouter.shouldSkipForPort` *and* the
  predicate run, so the port is checked twice per candidate.
- `@RouteCondition` is turned into a predicate that re-evaluates the annotation on every
  request (`DefaultRouteBuilder.java:882-887`).
- `toRouteInfo()` is called once per route when `DefaultRouter` is constructed
  (`DefaultRouter.java:110`), so the build-time objects are dead weight after startup but
  stay referenced through `RouteBuilder` beans.
- `DefaultRouteBuilder` is public API: it is extended by
  `management/.../AbstractEndpointRouteBuilder`, `function-web/.../AnnotatedFunctionRouteBuilder`,
  `GroovyRouteBuilder` and documented user code (`test-suite/.../docs/server/routes/MyRoutes.java`).
  Any redesign must keep it working as an *input* format.

### 2.2 Runtime model (immutable)

`DefaultRouteInfo` → `DefaultMethodBasedRouteInfo` (sealed) → `DefaultRequestMatcher`
(sealed) → `DefaultUrlRouteInfo` / `DefaultErrorRouteInfo` / `DefaultStatusRouteInfo`, plus
`http-server/.../ExecutableRouteInfo` for exception handlers.

This layer is in good shape: `DefaultRouteInfo` precomputes about twenty booleans, the
media-type lists, the `MessageBodyWriter`, the status and content disposition at
construction (`DefaultRouteInfo.java:92-168`). Two things stand out:

- `DefaultUrlRouteInfo` holds **both** `UriMatchTemplate` (legacy, regex based,
  `http/.../uri/UriMatchTemplate.java` + `UriTemplate.java`, ~1500 lines) and
  `UriTemplateMatcher` (`http/.../uri/UriTemplateMatcher.java`, the one that actually
  matches). The constructor parses the template string a second time
  (`DefaultUrlRouteInfo.java:147`). The legacy object is only used for
  `getUriMatchTemplate()` (routes endpoint, `BasicHttpAttributes.setUriTemplate`) and for
  the ambiguity statistics in `DefaultRouter.resolveAmbiguity`, which are computed with
  `Stream` pipelines on every call (`UriTemplate.java:183-203`).
- Argument binders are resolved lazily on first request with the registry passed in from
  the request path (`DefaultMethodBasedRouteInfo.resolveArgumentBinders`,
  `AbstractRouteMatch.java:296`). The registry is a singleton, so this dependency inversion
  only exists because `RouteBuilder`s cannot see it; the route info should be built with
  its binding plan.

### 2.3 Per-request model (`RouteMatch`)

`AbstractRouteMatch` (`router/.../AbstractRouteMatch.java`) is created for **every route
whose template matches the path**, not only for the winner (`DefaultRouter.java:257-263`).
Each instance allocates the object, `argumentValues`, `fulfilledArguments`,
`postponedArgumentBinders` and `pendingRequestBindingResults` arrays, plus the
`DefaultUriMatchInfo` with its `LinkedHashMap` of raw variables, and later a second
`LinkedHashMap` with URL-decoded values (`DefaultUriRouteMatch.getVariableValues`).

The `RouteMatch` interface mixes several concerns:

- identity of the matched route (`getRouteInfo`, `getVariableValues`),
- a binding state machine (`fulfillBeforeFilters`, `fulfillAfterFilters`, `isFulfilled`,
  `isSatisfied(name)`, `getRequiredInput`),
- invocation (`execute`, `call`, and the legacy `invoke(Object...)` that converts arguments
  by name through `ConversionService`, `AbstractRouteMatch.java:189-216`),
- resource ownership (`Closeable` since 5.0),
- and `AnnotationMetadataProvider` (used by security and other modules).

Because it is exposed through `RouteAttributes.getRouteMatch(request)` it is de facto
public SPI (micronaut-security, views, tracing read it), so it cannot simply be removed,
but it can become a thin facade over two internal objects: an immutable *match* (route
info + captured variables as a `String[]` aligned with the template's variable order) and
a per-invocation *binding* (values + state). See section 7.

---

## 3. Matching algorithm (`DefaultRouter`)

### 3.1 Data structures

- `Map<HttpMethod, UriRouteInfo[]>` (enum map) and `Map<String, UriRouteInfo[]>` for
  custom methods (`DefaultRouter.java:65-66`). Arrays are sorted once by
  `UriTemplateMatcher.compareTo` (longer literal length first, then fewer variables).
- Status and error routes are flat arrays.
- Filters are four lists split by "always matches" / "has precondition" and
  "pre-matching" / "post-matching", with the always-matching lists memoised into sorted
  `ArrayList`s.

There is no index on the path. Every request performs `O(R_method)` work where
`R_method` is the number of routes for that HTTP method.

### 3.2 Per-request flow for `findClosest`

1. `findInternal` (`DefaultRouter.java:700-742`): allocates an `ArrayList` sized to the
   whole method table, then for each route checks port, `permitsRequestBody`/consumes,
   produces and `matching(request)` predicates.
2. For every surviving route, `tryMatch(path)` (`DefaultUrlRouteInfo.java:175-181`):
   - `UriTemplateMatcher.tryMatch` strips a trailing slash with `substring`, scans for
     `?` and strips again (the path passed in never contains a query; this is per-route
     redundant work), then walks segments doing `startsWith` + `substring` for each
     literal and each `{var}` (`UriTemplateMatcher.java:258-334`). A three-segment route
     therefore allocates three to five strings per candidate per request.
   - Anything that is not a plain `{var}` followed by `/` or end of template (for example
     `{id:[0-9]+}`, `{+path}`, `{/path*}`, `{?q}`) falls back to a `java.util.regex`
     `Pattern` for the rest of the template (`UriTemplateMatcher.java:89-116, 316-329`).
   - A `DefaultUriRouteMatch` is created for each match.
3. If more than one route matched, `resolveAmbiguity` (`DefaultRouter.java:305-369`)
   re-checks media types on the *matches* (already checked on the infos in step 1), then
   compares `getPathVariableSegmentCount()` and `getRawSegmentLength()` of the legacy
   template, which allocate `Stream`s each time, then `ImplicitHeadRoutes.preferExplicit`.

Per request for a single `GET /users/{id}` candidate the routing step alone allocates on
the order of fifteen objects before any argument is bound (candidate list, match info,
variable map, four state arrays, match object, three substrings, attribute-map nodes).

### 3.3 Route misses are the most expensive path

`RequestLifecycle.onRouteMiss` (`http-server/.../RequestLifecycle.java:530-601`) calls
`router.findAny(request)` which scans **all routes of all methods** and materialises a
`RouteMatch` for each template that matches the path just to read its HTTP method and
media types, then builds three `HashSet`s. The resulting `HttpStatusException` then goes
through `onStatusError`: two linear scans of status routes, up to four scans of error
routes (`RouteExecutor.findErrorRoute`, `RouteExecutor.java:308-365`), then a bean lookup
`findBeanDefinition(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(...))` with
a freshly allocated qualifier, and finally the `ErrorResponseProcessor`. A scanner hitting
random paths makes the server do more work per request than a real request does.

### 3.4 Error and status route lookup

- `DefaultErrorRouteInfo.match` and `DefaultStatusRouteInfo.match` allocate an
  `Optional` **and a `RouteMatch`** for every candidate, before the closest one is chosen
  (`DefaultErrorRouteInfo.java:79-92`, `DefaultRouter.findRouteMatch` line 756).
- The class hierarchy of the thrown exception is rebuilt with
  `ClassUtils.resolveHierarchy` for every lookup with more than one candidate
  (`DefaultRouter.java:762`).
- Every response with status ≥ 400 runs `RequestLifecycle.handleStatusException`, which
  scans the status routes twice (local, then global) even when no status routes exist for
  that code (`RequestLifecycle.java:506-522`, `RouteExecutor.java:368-378`). An
  `int`-indexed table (`StatusRouteInfo[][]` by status code) would make the common "no
  status route" case a null check.

### 3.5 Versioning forces the slow path

`FilteredRouter` (`router/.../filter/FilteredRouter.java`) decorates the router when
`micronaut.router.versioning` is enabled and does not override `findClosest`, so the
default `Router.findClosest` → `FilteredRouter.findAllClosest` →
`find(request).collect(toList())` path runs. That path allocates a `Stream` pipeline per
request and the version predicate reads `@Version` from the executable method's annotation
metadata for every candidate on every request
(`router/.../version/RouteVersionFilter.java:174-176`). The route's version should be a
field of `UriRouteInfo` computed at startup, and `RouteMatchFilter` should have a
list-based entry point.

### 3.6 Concrete matching improvements (ranked by value ÷ risk)

1. **Normalise the path once per request**, not once per candidate. Strip the trailing
   slash in `DefaultRouter` and give `UriTemplateMatcher` an internal `match(String path,
   int from)` that walks with indexes instead of `substring`. No public API change.
2. **Separate "does it match" from "capture variables".** `UriRouteInfo.tryMatch` should
   first test the template and only allocate a `RouteMatch` for the winner. For templates
   without variables the test is `path.equals(template)`; those routes can live in a
   `HashMap<String, UriRouteInfo[]>` per method for an `O(1)` hit. Most controller routes
   are static, so this removes the scan for the majority of requests.
3. **Cache the ambiguity statistics** (`pathVariableSegmentCount`, `rawSegmentLength`)
   on `DefaultUrlRouteInfo` at construction and drop the `Stream` calls in
   `resolveAmbiguity`. This is a two-line change with a visible effect on any
   `/users/{id}` vs `/users/me` style API.
4. **Index templated routes by their leading literal segment** (a one-level trie or a
   sorted array with binary search on the first segment), with a separate "starts with a
   variable" bucket. This turns `O(R_method)` into `O(R_prefix)`.
5. **Do not build matches on a miss.** `findAny` should return route infos (or a small
   record of method + media types) so 404/405/415/406 decisions never allocate
   `RouteMatch` objects, and the `HashSet`s should be replaced with small arrays.
6. **Index status routes by code and error routes by `(originatingType, exceptionType)`**,
   and cache the resolved error route per `(declaringType, exceptionClass)` in a bounded
   `ConcurrentHashMap` (predicates and `Accept` filtering are applied after the cached
   candidate list). Cache `ExceptionHandler` bean definitions per exception class the same
   way in `RouteExecutor`.
7. **Retire the second template parser.** Keep `UriMatchTemplate` as the public type but
   make it a view over `UriTemplateParser` parts (no regex unless the template needs one),
   or expose `UriTemplateMatcher` on `UriRouteInfo` and deprecate `getUriMatchTemplate`.
   Halves template parsing at startup and removes a `Pattern` per route.
8. **Precompute the route version** and give `RouteMatchFilter` a list-based method so
   `FilteredRouter` can share `DefaultRouter`'s fast path.

Items 1, 3, 5 and 8 are internal and safe for a 5.3.x patch. Items 2, 4, 6 and 7 are
internal but touch `UriRouteInfo`/`Router` implementations, so they fit a 5.4 minor.

---

## 4. Request lifecycle and `RouteExecutor`

### 4.1 Flow per request

```
RoutingInBoundHandler.accept                      (http-server-netty)
  → new NettyRequestLifecycle(...).handleNormal
    → RequestLifecycle.normalFlow → runServerFilters
      → new FilterRunner(preMatchingFilters, ...) { anonymous subclass with routeMatch field }
        → pre-matching filters
        → doRouteMatch: router.findClosest + RouteAttributes.set* + setUriTemplate
        → findFiltersAfterRouteMatch: router.findFilters(request)
        → post-matching filters
        → provideResponse → executeRoute
            → fulfillArguments (fulfillArgumentRequirementsBeforeFilters, form completer, ROUTE_WAITS_FOR)
            → RouteExecutor.callRoute
                → executor selection (routeInfo.getExecutor, memoised)
                → executeRouteAndConvertBody
                    → propagatedContext.plus(new ServerHttpRequestContext(request)).propagate
                    → fulfillAfterFilters → routeMatch.execute()
                    → createResponseForBody → finaliseResponse
            → handleStatusException → onErrorNoFilter
        → response filters
  → writeResponse
```

### 4.2 Findings

- **Naming no longer matches behaviour.** `fulfillArgumentRequirementsBeforeFilters` runs
  *after* all request filters (`RequestLifecycle.executeRoute`, line 177-184, is called from
  `provideResponse`, the terminal of the filter chain). The real distinction is
  "binders that can run now" versus `PostponedRequestArgumentBinder`s that must run inside
  the route's propagated context. The two phases should be renamed (for example
  `bindImmediate` / `bindDeferred`) or merged into one call on the binding plan.
- **The `ServerHttpRequestContext` is added twice.** `RoutingInBoundHandler.accept`
  already wraps the lifecycle in `PropagatedContext.plus(new ServerHttpRequestContext(...))`
  (`RoutingInBoundHandler.java:240`), and `executeRouteAndConvertBody` adds another
  (`RouteExecutor.java:492`). The second one is only needed when a filter replaced the
  request object; it should be conditional on `request != original`, since each `plus`
  copies the element array and the lookup walks the array from the end.
- **Response strategy is re-decided per call.** `createResponseForBody`
  (`RouteExecutor.java:510-568`) walks `body == null` / `String` / `HttpStatus` /
  `isImperative` / `isAsync` / `isReactive` / `Publishers.isConvertibleToPublisher(body)`
  / `isSuspended`, and `fromImperativeExecute` repeats `instanceof MutableHttpResponse` /
  `HttpResponse`. All of this is decidable from the `ReturnType` at startup. A per-route
  `ResponseStrategy` enum (VOID, VALUE, HTTP_RESPONSE, ASYNC, REACTIVE_SINGLE,
  REACTIVE_STREAM, SUSPEND) stored on `DefaultRouteInfo` would turn this into one switch
  and let each branch be a small, testable method.
- **Streaming responses pay per-request setup cost.** `processPublisherBody` calls
  `findExecutor(routeInfo)` (`RouteExecutor.java:381-392`), which goes through
  `ExecutorSelector.select` and reads `@ExecuteOn`, `@Blocking`, `@NonBlocking` annotation
  metadata on every response, whereas `callRoute` uses the memoised
  `routeInfo.getExecutor(threadSelection)`. `applyExecutorToPublisher` then creates a new
  Reactor `Scheduler` (`Schedulers.fromExecutor` / `fromExecutorService`) and a new
  `ContextPropagatingExecutorService` per response (`RouteExecutor.java:394-415`).
  Both should be memoised on the route info (or in a small executor → scheduler map).
- **Reactive single results go through a long operator chain.** `fromReactiveExecute`
  builds `Flux.from(publisher).flatMap(...).switchIfEmpty(Mono.fromSupplier(...))
  .contextWrite(...)` and then `ReactivePropagation.propagate` and
  `ReactiveExecutionFlow.fromPublisher` (`RouteExecutor.java:630-676`). For a `Mono<T>`
  route this is six to eight operator objects plus subscriber chain per request; a direct
  subscriber that completes an `ExecutionFlow` would be cheaper and easier to reason about.
- **`RequestLifecycle` duplicates the "run, then status route, then error" pattern**
  four times (`executeRoute`, `handleErrorRoute`, `handlerExceptionHandler`,
  `onStatusError`), each with slightly different `onErrorResume` nesting. One private
  `runAndPostProcess(flow, request, routeInfo, ctx)` would remove that.
- **Per-request anonymous `FilterRunner` subclasses** are created in `runServerFilters`,
  `runWithFilters` and `runResponseFilters`, each capturing the lifecycle and overriding
  four to six methods. This is allocation-cheap but makes the control flow hard to follow:
  routing happens inside a filter-runner callback (`doRouteMatch`), and the route match is
  stored in a field of the anonymous class *and* in the request attributes.
- **Static file lookup happens after all filters and only on a miss.** `findFile` is
  called from `provideResponse` when there is no route (`RequestLifecycle.java:443`), so
  static resources always pay the full route scan and the miss path first.
- **Attributes.** `setRouteAttributes` does three `HashMap` puts through
  `MutableConvertibleValuesMap` on the request (`ROUTE_MATCH`, `ROUTE_INFO`,
  `URI_TEMPLATE`), `finaliseResponse` does two more on the response, and every reader goes
  through `Optional` (`RouteAttributes`, `BasicHttpAttributes.getRouteWaitsFor`,
  `FormFactory.getCompleterOrNull`). `NettyHttpRequest.cleanup` then special-cases those
  keys by string identity to avoid an `instanceof` (`NettyHttpRequest.java:416-427`).
  A `ServerHttpRequest` accessor with typed fields (`routeMatch()`, `routeInfo()`,
  `uriTemplate()`), with `RouteAttributes` delegating to it when available, removes the
  map traffic and the cleanup special case while keeping the attribute API for others.

### 4.3 Concrete executor improvements

1. Memoise the streaming executor and its `Scheduler` per route; drop the duplicate
   `ServerHttpRequestContext`. Internal, patch-safe.
2. Add `ResponseStrategy` to `DefaultRouteInfo` and rewrite `createResponseForBody` as a
   switch; keep the current method as the fallback for third-party `RouteInfo`s.
3. Introduce a single post-processing helper in `RequestLifecycle` and make routing an
   explicit step of the lifecycle rather than a callback from `FilterRunner`
   (`FilterRunner` would receive a `RouteMatch` supplier instead of overriding
   `doRouteMatch`).
4. Typed route fields on the server request, `RouteAttributes` delegating to them.
5. Move static-resource matching into the route table as a lowest-priority route source
   (this is exactly what the functional-routes branch's `fn-routes-static-resources`
   line does), so it participates in ordering and does not depend on the miss path.

---

<!-- SECTIONS 5 (binding), 6 (filters), 7 (target design) and 8 (functional-routes integration) follow -->
