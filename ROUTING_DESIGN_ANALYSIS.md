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
- **Argument binding re-derives per-route facts on every request**: annotation reads in
  the common binders, attribute-map lookups to learn whether a query variable is
  exploded, three conversions for one primitive, and stream pipelines for every
  unannotated argument. A per-route binding plan removes most of it.
- **The filter runner is a state machine over a mutable shared iterator**, path
  patterns are re-tokenised on every request, and around-style filters do not compose
  with `@ResponseFilter` methods as an onion (section 6.3 b), which needs a test and a
  decision.
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

## 5. Argument binding

### 5.1 Flow

1. The route match is created (`DefaultUrlRouteInfo.tryMatch`) with its four per-request
   arrays and stored as a request attribute.
2. All request filters run; the route is executed as the terminal of the filter chain
   (`RequestLifecycle.provideResponse` → `executeRoute`).
3. `RequestArgumentSatisfier.fulfillArgumentRequirementsBeforeFilters` (a two-method
   pass-through; `NettyRequestArgumentSatisfier` only calls `super`) →
   `AbstractRouteMatch.fulfillBeforeFilters` (`AbstractRouteMatch.java:289-326`): for
   each argument, first `getVariableValues().get(name)` (a template variable wins over
   any annotation), else the binder from `routeInfo.resolveArgumentBinders`, dispatching
   on `instanceof PostponedRequestArgumentBinder` / `UnmatchedRequestArgumentBinder`;
   `PendingRequestBindingResult`s are parked.
4. `RequestLifecycle.fulfillArguments` starts the form completer if any and waits on the
   `ROUTE_WAITS_FOR` attribute, an `ExecutionFlow` that Netty body binders append to
   (`BasicHttpAttributes.addRouteWaitsFor`).
5. After the executor hop, `RouteExecutor.executeRouteAndConvertBody` calls
   `fulfillAfterFilters` (postponed binders only) and `execute()`, which resolves parked
   results, fills `Optional.empty()`/`null` defaults or throws
   `UnsatisfiedRouteException`.

The two "pending" mechanisms (returning a `PendingRequestBindingResult` and calling
`addRouteWaitsFor`) are coupled only by convention; a binder that forgets the second
produces a silent `UnsatisfiedRouteException` at `execute()`.

### 5.2 Cost of a typical request

For `GET /foo/{id}?q=x` bound to `(@PathVariable String id, @QueryValue String q)` on a
Netty request, steady state:

| Step | Allocations | Source |
|------|-------------|--------|
| match + state arrays | 5 | `AbstractRouteMatch.java:87-90` |
| `getVariableValues()` decoded map, entries, lambda | 3-4 | `DefaultUriRouteMatch.java:70-86` |
| conversion context for `q` (context + eager `ArrayList(3)` + `Object[3]`) | 3 | `AbstractRouteMatch.java:373-379`, `DefaultArgumentConversionContext.java:44` |
| `getRouteMatchInfo` attribute lookup to learn whether `q` is an exploded variable | ~7 (`Argument.of`, second context, `Optional`s, lambda) | `QueryValueArgumentBinder.java:130-134`, `ValueResolver.java:52-54` |
| `ConvertibleMultiValuesMap.get` (`unmodifiableList`, `Optional`) + `orElseGet` lambda | 3 | `AbstractArgumentBinder.java:219-233` |
| `doConvert`: second `conversionService.convert` on an already typed value, anonymous `BindingResult` | 3 | `AbstractArgumentBinder.java:280-296` |
| `getRouteWaitsFor` attribute lookup + `then` lambda + `ExecutionFlow.just` | ~6 | `BasicHttpAttributes.java:87-88`, `RequestLifecycle.java:662-668` |

Roughly a dozen allocations per bound query argument, of which two or three are
intrinsic. Two more findings:

- **Primitives are converted three times.** `ConvertibleMultiValuesMap.get` converts,
  `doConvert` converts again, then `AbstractRouteMatch.convertValue` tests
  `argument.getType().isInstance(value)` (`AbstractRouteMatch.java:463`), which is false
  for `int.class` against an `Integer`, so it allocates a third context and converts a
  third time. `getWrapperType()` fixes this in one line.
- **Unannotated arguments run two stream pipelines twice.** An unannotated `String q`
  gets `DefaultUnmatchedRequestArgumentBinder`, which is both postponed and unmatched, so
  it runs in both phases, and each phase builds
  `Stream.concat(...).filter(...).toList()` plus two `ArrayList`s before trying the
  binders (`DefaultUnmatchedRequestArgumentBinder.java:57-65, 78-82, 119-121`).
  `@RequestBean` is worse: it re-resolves the introspection, builds a property map and
  calls `registry.findArgumentBinder` (which calls `createSpecific`) per property per
  request (`RequestBeanAnnotationBinder.java:71-164`).

### 5.3 Binder lookup

`DefaultMethodBasedRouteInfo.resolveArgumentBinders` caches the binder array per route on
first request (racy but idempotent). `DefaultRequestBinderRegistry.findArgumentBinder`
does a runtime `getAnnotationTypeByStereotype(Bindable)` lookup, then either a
30-entry cache keyed by a fresh `TypeAndAnnotation` or a `typeHashCode()`-keyed map
(distinct types with equal hash collide silently). It never returns empty, so the
`Optional` and the null checks in callers are dead. `createSpecific` (4.8) is the only
precomputation hook and is used well by `CookieObjectArgumentBinder`, but
`QueryValueArgumentBinder`, `DefaultBodyAnnotationBinder`, the Netty upload binders and
`RawFormFieldArgumentBinder` still read annotation metadata on every request
(`hasAnnotation(QueryValue)`, `hasAnnotation(Format)`, `stringValue(Bindable.NAME)`).

### 5.4 Design issues

1. **Stale phase names**: both phases run after the request filters; what separates
   them is the async wait and the executor hop. A postponed binder returning a pending
   result in phase two can never be awaited and is treated as unsatisfied.
2. **Multi-owner mutable state**: `RouteMatch` is a state machine guarded by
   `IllegalStateException`s, a request attribute, a binder-visible object
   (`RouteMatchArgumentBinder`), and it hands out its mutable `LinkedHashMap`.
   `fulfill(Map)`, `isFulfilled()` (which has side effects), `isSatisfied(String)` and
   `getRequiredArguments()` have no callers in main code.
3. **Two code paths for path variables** with different semantics
   (`AbstractRouteMatch.convertValue` vs `PathVariableAnnotationBinder.bind`; only the
   latter honours `defaultValue` and fallback names).
4. **Ownership**: binding logic lives in `router` and dispatches on `http` marker
   interfaces per request; `RequestArgumentSatisfier` is an indirection reached through
   a package-private field of `RouteExecutor`.
5. **`BindingResult` protocol**: `isSatisfied()` is derived from
   `getConversionErrors().isEmpty()` so callers recompute error lists three to four
   times per result; `UNSATISFIED` is compared by identity; the composite's
   `isPending()` is `allMatch` over a possibly empty list. It works because
   `failOnConversionErrors` runs afterwards.
6. **Duplication**: `MappedBindingResult` / `MappedPendingRequestBindingResult`;
   `NettyBodyAnnotationBinder` overrides all hooks of `DefaultBodyAnnotationBinder`;
   `DefaultRequestBinderRegistry.convertBodyIfNecessary` re-implements body binding for
   `HttpRequest<T>` arguments; `DefaultExecutableBinder` is a parallel binder unused by
   the server.
7. **Three exception families** for "argument missing" (`UnsatisfiedArgumentException`,
   `UnsatisfiedRouteException`, `ConversionErrorException`) with three handlers, and a
   special case in `RouteExecutor.findErrorRoute` that recognises only one of them.
8. **The benchmark measures the wrong thing**: `RequestArgumentSatisfierBenchmark` binds
   `/arguments/foo/{name}/{age}`, where both arguments are template variables, so no
   `ArgumentBinder` ever runs, on a non-Netty `SimpleHttpRequest`; its `main` runs
   `AroundCompileBenchmark`.

### 5.5 Concrete binding improvements (ranked)

1. **Per-route binding plan.** Extend `resolveArgumentBinders` into a plan per argument:
   kind (template-variable index, eager binder, postponed, unmatched-both-phases), flags
   (`optional`, `nullable`, `primitive`, needs-conversion), the specific binder and a
   precomputed `ArgumentConversionContext` template. `fulfillBeforeFilters` becomes a
   switch over a byte array; `getVariableValues()` is replaced by an index into the
   match's captured `String[]`. Publish it safely (built with the route info).
2. **Remove attribute lookups from the hot path.** Pass the exploded-variable flag into
   `createSpecific` (or the plan) so `QueryValueArgumentBinder` and
   `PathVariableAnnotationBinder` stop calling `getRouteMatchInfo`; use the single-argument
   `getAttribute` plus `instanceof` in `RouteAttributes` / `BasicHttpAttributes`.
3. **Fix primitive re-conversion** (`getWrapperType().isInstance`) and let
   `AbstractArgumentBinder.doConvert` skip conversion when the value already matches.
4. **`DefaultUnmatchedRequestArgumentBinder`**: partition binders once in
   `createSpecific`, loops instead of streams, lazy lists, correct empty `isPending()`.
5. **Cheaper results**: a final `BindingResult` value class with a precomputed error
   list; read `getConversionErrors()` once; lazy error list in
   `DefaultArgumentConversionContext`.
6. **`RequestBeanAnnotationBinder` plan** resolved in `createSpecific`.
7. **Unify pending and `ROUTE_WAITS_FOR`**: let `PendingRequestBindingResult` expose its
   completion flow and wait on the parked results, removing the parallel attribute.
8. **Rename and re-own the phases** (event-loop phase vs executor phase), drop
   `RequestArgumentSatisfier`, split `RouteMatch` (section 7).
9. **Rewrite the benchmark** to cover query, header, body and unannotated arguments on a
   Netty request, separating routing from binding.

Items 1-5 should cut per-argument allocations from about twelve to three or four and
remove per-request annotation reads for the common binders; a corrected benchmark is
needed to confirm.

---

## 6. Filter pipeline

### 6.1 Flow

- **Registration.** `@ServerFilter` methods become `DefaultFilterRoute`s with a lazy
  `Supplier<GenericHttpFilter>` that builds a `MethodFilter` on first use; legacy
  `HttpServerFilter` beans become `AroundLegacyFilter`s. `FilterOrder.Dynamic`
  re-evaluates `bean instanceof Ordered` on every `getOrder()` call. Pre-matching is
  detected by stereotype name because `http` cannot see `http-server`'s `@PreMatching`.
- **Classification.** `DefaultRouter` splits filter routes into four lists
  ({pre-matching, post-matching} × {always matches, has precondition}); the always-match
  lists are memoised and sorted once. `isMatchesAll` requires no `@FilterMatcher`, no
  `methods` and only `/**` patterns.
- **Resolution per request.** `RequestLifecycle.runServerFilters` builds a new anonymous
  `FilterRunner` subclass, gets the pre-matching filters, and after `doRouteMatch` calls
  `router.findFilters(request)`, which reads the route match back from the request
  attribute for `@FilterMatcher` and runs `DefaultFilterRoute.match(method, path)` for
  every precondition filter: `HashSet.contains` on methods, then `PathMatcher.matches`
  per pattern, `Optional` wrap, sort, `unmodifiableList`. With no precondition filters
  the memoised list is returned directly; a single filter with a narrower pattern or
  `methods` puts the whole matching and sorting cost on every request.
- **Execution.** `FilterRunner.run` copies the enabled filters into a fresh `ArrayList`
  (`filterFilters`), and if pre-matching filters exist, appends a
  `RouteMatchResolverHttpFilter` that, when reached, performs the route match and then
  *inside a `finally`* rewrites the live list through the shared `ListIterator`: removes
  itself and every preceding filter, appends the post-match filters and rewinds
  (`FilterRunner.java:486-499`). `filterRequest` walks forward; filters with a
  continuation (`MethodFilter` with `FilterContinuation`, and every `AroundLegacyFilter`)
  get a downstream lambda that recursively calls `filterRequest` on the same iterator;
  the terminal `provideResponse` runs the route. `filterResponse` walks backwards from
  wherever the iterator stopped, and every time a filter *replaces* the response
  `processResponse` runs status routes; failures go through `processFailure` →
  `onErrorNoFilter`.
- **Legacy and reactive continuations** convert the whole downstream into a Reactor
  `Publisher` and back (`AroundLegacyFilter.java:118-142`,
  `MethodFilter.ReactiveContinuationImpl`), after which every `tryCompleteValue` fast
  path fails and the response walk runs through Reactor operators.

### 6.2 Hot-path costs

- Per request, baseline: the anonymous `FilterRunner` plus an anonymous `BiFunction` that
  only throws (`RequestLifecycle.java:424-429`, could be a constant), two `ArrayList`
  copies and backing arrays, a `ListIterator`, one `FilterContext`, a bound method
  reference and a second `FilterContext` for `map(context::withResponse)`.
- `FilterRunner.sort` → `checkOrdered` allocates a `Stream` pipeline per call
  (`FilterRunner.java:105-109`) to verify a property the sealed hierarchy already
  guarantees; it runs on every `findFilters` call when precondition filters exist.
- **Pattern matching**: `AntPathMatcher.doMatch` tokenises *both* the static pattern and
  the path on every call via `StringUtils.tokenizeToStringArray` (`StringTokenizer` +
  `ArrayList` + `String[]` + substring per segment) and calls `toCharArray()` on both
  sides per segment (`core/.../AntPathMatcher.java:102-108, 240-242`). Nothing is cached
  per route or per pattern.
- Per `MethodFilter` invocation: `MutablePropagatedContext.of(...)` and a
  `FilterMethodContext` even when the method takes neither, an `Object[] args`, a
  `ConversionContext.of(argument)` per binder-backed argument even though the argument
  is static, `request.mutate()` (a `NettyMutableHttpRequest` view) for
  `MutableHttpRequest` parameters, a composed `Predicate` chain for the filter condition,
  an `ImperativeExecutionFlowImpl` from the return handler.
- `CorsFilter.isEnabled` is evaluated on every list copy and allocates
  `request.getOrigin()`; `getConfiguration(request)` is recomputed in the request and
  the response filter.
- Once anything in the chain is asynchronous, every `map`/`flatMap`/`onErrorResume`
  in `FilterRunner` and `RequestLifecycle` allocates a `DelayedExecutionFlowImpl.Step`.

### 6.3 Design issues

a. **`FilterRunner` is a template-method state machine over a mutable shared iterator.**
   The `ListIterator` is the state: captured by continuation lambdas, consumed
   recursively, and surgically rewritten by `RouteMatchResolverHttpFilter`. Correctness
   depends on every mutation going through the same iterator instance and on the
   `finally` running exactly once. It is hard to reason about and impossible to make
   immutable or reusable.

b. **Around-style filters and `@ResponseFilter` methods do not compose as an onion.**
   For `[around F1 (order 0), @ResponseFilter F2 (order 1)]`, F1's `proceed()`
   downstream is `filterRequest` on the remaining *request* filters plus the route
   (`FilterRunner.java:310-315`); F2 is not a request filter, so F1 observes the raw
   route response. F2 then runs later in `run()`'s backward walk on F1's result
   (`FilterRunner.java:252-254, 349-354`), while `AroundLegacyFilter.isFiltersResponse()`
   is `false`. In an onion, the inner F2 should see the raw response and F1 should see
   F2's output. Two around filters nest correctly (`FilterRunnerSpec`), but no test
   covers the mixed case. This needs a test and a decision: either document it or make
   the continuation's downstream include the downstream response filters (a visible
   behaviour change to gate behind a switch or a major).

c. **Too many representations of "a filter"**: `FilterRoute` (mutable builder + lazy
   supplier + matcher) extends `HttpFilterResolver.FilterEntry`; `DefaultFilterEntry`
   duplicates it for the client; `GenericHttpFilter` is a sealed marker with two
   `@Deprecated(forRemoval)` methods; `InternalHttpFilter` is the real contract. Skipping
   a filter is expressed five ways (patterns/methods, `@FilterMatcher`,
   `ConditionalFilter`, `FilterArgumentBinderPredicate`, `Toggleable` for legacy) and
   evaluated in three places. `DefaultRouter` implements `HttpServerFilterResolver`
   (`resolveFilterEntries` / `resolveFilters`, `DefaultRouter.java:790-833`) which no
   server code calls, and `resolveFilterEntries` has the `@FilterMatcher` condition
   inverted relative to `findFilters` (line 797 vs 604).

d. **Routing is owned by the filter chain through server hooks in a shared class.**
   `FilterRunner` (module `http`, shared with the client) exposes `doRouteMatch` /
   `findFiltersAfterRouteMatch` that throw by default; `RequestLifecycle` fills them in
   an anonymous subclass and stashes the match in a field with a comment admitting the
   workaround. The match is also communicated through request attributes, which
   `findFilters` reads back; the explicit `findFilters(request, routeMatch)` overload is
   unused by the lifecycle.

e. **`RouteMatchFilter` / `FilteredRouter` is an unrelated "filter" concept** living in
   the router layer (section 3.5).

f. **Error handling is layered three times** (`executeRoute`,
   `provideResponseAndHandleErrors`, `processFailure`), `onError` re-runs the request
   filters for an exception raised outside the chain, and `onWriteError` re-resolves and
   re-runs response filters including pre-matching ones, which are request-only.

g. **Request mutation after matching is only half supported.** A post-match request
   filter that changes the URI keeps the old `RouteMatch` (path variables from the
   original path) while header/query binding happens against the new request. A filter
   returning a different `HttpRequest` implementation loses `ROUTE_MATCH` / `ROUTE_INFO`,
   which changes error-route resolution and `@FilterMatcher` behaviour (a null match
   disables the check, so matcher-restricted filters are *included*). The
   functional-routes branch's `MutableServerRequest` / `UriChangeAwareRequest` /
   `BodyChangeAwareRequest` are the answer to this (section 8).

h. **Thread switching is implicit**: `@ExecuteOn` on a filter method becomes
   `ExecutionFlow.async` per invocation; response filters after a blocking route run on
   whatever thread completes the flow; a legacy or reactive continuation moves everything
   to Reactor scheduling. The public API does not say on which thread a
   `@ResponseFilter` runs.

### 6.4 Concrete filter improvements (ranked)

1. **Precompile filter patterns.** Store pre-tokenised segments (or a compiled matcher)
   per `FilterRoute`, match with an index-based scanner, and replace the
   `HashSet<HttpMethod>` with a bitmask. Low risk, removes the dominant allocation source
   for apps with patterned filters.
2. **Per-route filter chain caching with a safe fallback.** For each `UriRouteInfo`
   precompute the post-match chain when every precondition pattern is decidable against
   the route template (`/api/**` vs `/api/users/{id}`); fall back to per-request matching
   otherwise. Drop the `Stream` in `checkOrdered`; resolve `FilterOrder.Dynamic` to an
   `int` once.
3. **Immutable chain with an explicit cursor** instead of list surgery: `pre[]` /
   `post[]` arrays, `isEnabled` evaluated lazily in the loop, pre-matching modelled as a
   two-phase cursor. Medium risk because continuations rely on the shared iterator today.
4. **Trim `MethodFilter` per-invocation allocations**: contexts only when an argument
   needs them, precomputed conversion contexts, flattened predicate array, "unchanged"
   signalled without a new flow.
5. **Stateless server callbacks** instead of a per-request anonymous `FilterRunner`
   subclass: a `ServerFilterCallbacks` object owned by `RequestLifecycle`, per-request
   state passed explicitly, `FilterRunner` free of routing hooks.
6. **Decide and test the around/response composition (issue b).**
7. **Simplify the SPI**: remove deprecated members from `GenericHttpFilter`, delete the
   unused `HttpServerFilterResolver` implementation in `DefaultRouter`, fold
   `FilterRoute` / `FilterEntry` into one immutable descriptor, make
   `findFilters(request, routeMatch)` the only resolution entry point.
8. **Small wins**: constant `BiFunction`, cache `CorsFilter` configuration per request,
   no `Optional` in `RouteAttributes` on the hot path, skip pre-matching filters in
   `runResponseFilters`.

---

## 7. Target design for the mainline

This section describes where the pieces above converge. It is deliberately compatible
with the public types (`Router`, `RouteBuilder`, `RouteInfo`, `RouteMatch`,
`RouteAttributes`, `GenericHttpFilter`) that other Micronaut modules depend on; the
changes are in the implementations and in new internal types.

### 7.1 Route table as a value

```
RouteBuilder beans ─┐
HttpRoutes beans ───┼─► RouteTable (immutable)           ◄── RouteSource.snapshot()
generated routes ───┘      ├─ per method: exact-path map + prefix index + ordered array
                           ├─ status routes indexed by code
                           ├─ error routes indexed by (originatingType, exceptionType)
                           └─ filter chains: always/pre-matching/precondition arrays
DefaultRouter = facade over one RouteTable (atomically replaceable)
```

- `DefaultRouter` keeps its constructor and API but delegates to a `RouteTable`
  built by a `RouteTableFactory`. This is what the functional-routes branch calls
  `DefaultRouteTable`, except that a table is a value with indexes rather than a whole
  router, so `RouteSourceRouter` does not have to re-implement `Router` to concatenate
  tiers: tiers are just an ordered list of tables consulted by the same matching code.
- `UriRouteInfo` gains the index keys the branch already defines
  (`requiredPrefix`, `rawLength`, `pathVariableCount`, `patternVariableCount`) and a
  `matches(path)` / `capture(path)` split; the legacy `UriMatchTemplate` becomes a view.
- Matching: exact-path hash hit → prefix-index candidates → ordered scan; ambiguity on
  route infos; a single `RouteMatch` allocated for the winner.

### 7.2 Split `RouteMatch`

- `UriMatch` (immutable): route info + captured variables as `String[]` in template
  order (+ lazily decoded values), `uri`. Shared by 404/405 analysis, filters, security.
- `RouteInvocation` (per request, internal): the argument values, the binding plan cursor
  and the pending results. Created by `RouteExecutor`, never stored in attributes.
- `RouteMatch` stays as the public facade implemented by a small adapter so
  `RouteAttributes.getRouteMatch(request)`, `getAnnotationMetadata()`,
  `getVariableValues()` and `execute()` keep working; `fulfill(Map)`,
  `invoke(Object...)`, `isSatisfied(name)`, `getRequiredArguments()` are deprecated for
  removal.

### 7.3 Binding plan and response strategy on the route info

Both are computed when the table is built (section 5.5 item 1, section 4.3 item 2), so
`RouteExecutor` becomes: select executor (memoised) → run plan → invoke → switch on
strategy → finalise. The plan is where `PathVariables` for functional routes and
typed-body handlers plug in without touching `AbstractRouteMatch`.

### 7.4 Typed per-request server state

A `ServerHttpRequest` accessor (`routeMatch()`, `routeInfo()`, `uriTemplate()`,
`routeWaitsFor()`, `formCompleter()`) implemented by `NettyHttpRequest` with plain
fields; `RouteAttributes` / `BasicHttpAttributes` / `FormFactory` delegate to it when the
request implements it and fall back to attributes otherwise.

### 7.5 Filter chain as data

Per table: `InternalHttpFilter[] preMatching`, `alwaysPostMatching`, and a
`PreconditionFilter[]` with precompiled path matchers and method bitmask; per route, a
cached post-match chain where decidable. `FilterRunner` takes immutable arrays and a
`RouteMatcher` callback and keeps no routing hooks; `RequestLifecycle` owns routing.
`RouteFunctionFilter` and `MutableServerRequest` from the branch slot in here.

### 7.6 Compile-time route tables

With `RouteDefinitions` (annotations resolved by name) and the `CompiledRouteMatcher` SPI
from the branch, a `TypeElementVisitor` can emit, per controller, an enum-backed matcher
and the index keys, so at runtime no template is parsed, no regex is compiled, and exact
routes are a switch. This is the natural end state for GraalVM startup and for the
"declared routes" feature, and it is why step 11 in section 8.5 should keep the
`IndexedRouteDeclaration` shape.

### 7.7 How to measure

- Extend `benchmarks` with a JMH suite that separates routing (`findClosest` on tables of
  10 / 100 / 1000 routes, static and templated, hit and miss), binding (query, header,
  body, unannotated, `@RequestBean` on a Netty request) and filters (0 / 1 patterned /
  1 around + 1 response). `ControllersBenchmark` and `FullHttpStackBenchmark` already
  cover the end-to-end path and should be run before and after each step.
- Track allocation per request with the JMH GC profiler; the targets above are
  expressed in allocations because that is what moves the event-loop throughput.

---

## 8. The `functional-routes` branch and how to fold it into 5.3.x

### 8.1 What the branch is

`origin/functional-routes` carries 153 commits over `5.3.x`, but the routing work is
exactly seven commits (`62a382a037^..2f87054e30`, 277 files, about +43k/-1k lines).
Commits 1-5 (about 1,300 lines) add the `RouteSource` / `RouteTable` / tier machinery;
commit 6 (`5eb11ccb2f`) is a monolith with the `HttpRoutes` builder, `RouteAssembly`,
`RouteIndex`, compiled matchers, locators, the filter-pipeline changes, a new
forms/async-body subsystem, Netty changes, thirteen guide pages and 79 test files; commit
7 extracts `RouteSpec`. Main-source footprint: `router` +14.5k, `http-server` +3.9k,
`http` +2.7k, `http-server-netty` +0.15k.

Everything new is `@Experimental`, and the public builder/spec types are `sealed`
("Micronaut implements them, the application uses them").

### 8.2 User-facing model

```java
@Singleton
public class HelloRoutes implements HttpRoutes {
    public void routes(HttpRouteBuilder routes) {
        routes.GET("/hello/{name}", (request, pathVariables) ->
            HttpResponse.ok("Hello " + pathVariables.getString("name"))
                .contentType(MediaType.TEXT_PLAIN_TYPE));
    }
}
```

- `HttpRouteBuilder` (91 methods): `GET/POST/...(uri, RequestHandler)`, typed body
  variants (`POST(uri, Class<B>, BodyRequestHandler<B>)`), form variants, `async*`
  variants returning `CompletionStage`, generic `handle(HttpMethod|Set|String, uri, ...)`,
  `error(Class<E>, ErrorRouteHandler<E>)`, `status(HttpStatus, StatusRouteHandler)`,
  `locate(prefix, LocatorHandler<T>, Function<T, RouteTable>)`, `filter(patterns)`,
  `group(...)`, `path(prefix, ...)`, plus a path-less overload of every shortcut.
- `RouteSpec` (shared by a route and a group): `consumes/produces`, `executeOn`,
  `nonBlocking`, `annotate(...)`, `attribute(...)`, `where(Predicate)`, `order(int)`,
  `port(...)`. Group settings are defaults for routes declared inside the group lambda.
- `RouteFilterSpec`: `before/beforeReplacing/after/afterReplacing` × plain/executor/async
  × with/without `MutablePropagatedContext` = 24 methods over 16 functional interfaces.
- `RequestPredicates` (`header`, `queryParam`, `accept`, `contentType`, `method`,
  `all/any`) for `where(...)`.
- `RouteSource` (ordered, `RouteTable snapshot()` called once per request) and
  `RouteTableFactory` (`build(Consumer<RouteBuilder>)`, `buildHttpRoutes(HttpRoutes)`,
  located variants) for tables that change at runtime, for example per tenant.

### 8.3 Internal architecture

- **`HandlerMethod`** turns a lambda into an `ExecutableMethod` / `MethodExecutionHandle`
  with synthesised `Argument[]` (`request`, `pathVariables`, `@Body body`, `form`), a
  return type and layered annotation metadata. The only hook in the existing binding code
  is a `PathVariables` special case in `AbstractRouteMatch.fulfillBeforeFilters`. Every
  other part of the pipeline (binders, body handlers, CORS, 405/415/406, versioning,
  `@Error`, executor selection, security) is reused unchanged. This is the strongest
  design decision in the branch: it is not a second request model.
- **`RouteAssembly`** (1,774 lines, `@Internal public`) is the extraction of
  `DefaultRouteBuilder`'s inner route classes plus groups, group filters, group error and
  status routes, declared (lazy) routes and implicit HEAD routes. `DefaultRouteBuilder`
  keeps its public API and delegates to it. `AnnotatedMethodRouteBuilder` shrinks by
  340 lines: the nine copy-pasted lambdas become `RouteDefinitions.resolve(...)`
  returning records, with annotations looked up by name so the same code can run in an
  annotation processor.
- **`RouteIndex`**: per HTTP method, a frozen character trie over each route's
  `getRequiredPrefix()` (the leading literal), answering `candidates(path)` with a bitset
  → `int[]` of route positions; routes with no literal prefix are always candidates. The
  result is provably the same as the linear scan because the index only skips routes that
  cannot match. This is the item this document's section 3.6 asks for and it already
  exists.
- **Compiled routes** (`spi.CompiledRouteMatcher`, `IndexedRouteDeclaration`,
  `LazyUriRouteInfo`, `DefaultRouter.findCompiled`): an annotation processor can emit an
  enum-backed matcher (`int match(HttpMethod, String path, String[] variables)`). At
  startup the router computes per route whether it is "exclusive" (cannot overlap any
  other route of the method); exclusive hits skip `UriTemplateMatcher` entirely,
  non-exclusive ones fall through to the generic path so ambiguity rules still apply.
  A proof module (`test-suite-http-routes-custom`) shows a `TypeElementVisitor`
  generating such a matcher.
- **Ambiguity**: existing media-type and specificity rules, then a third key
  `fewestPatternVariables` (`/t/{id}` beats `/t/{id:.+}`; `UriTemplateMatcher.compareTo`
  gets the same key), then `ImplicitHeadRoutes`, then `RouteOrders.preferLowest`.
- **`RouteSourceRouter`** decorates the `Router` when a `RouteSource` bean exists: it
  asks the application router first, then each table in order, returning the first
  non-empty result, and passes the composed `RouteMatchFilter` predicate into each tier's
  `findAllClosest(request, filter)` so versioning is applied before ambiguity
  resolution. A `DefaultRouteTable` is a full `DefaultRouter` instance.
- **Locators**: a route whose target is a `RouteLocator` resolves a target object
  (tenant, workspace) from the prefix variables, then matches the remainder of the path
  against that target's table. Async locators throw `PendingLocation`, which
  `FilterRunner.doRouteMatchAsync` (new hook) turns into a re-match when the stage
  completes.
- **Filters**: `RouteFunctionFilter` (a new `InternalHttpFilter`) executes the
  functional filters; `MutableServerRequest` is a wrapper that is simultaneously
  `MutableHttpRequest` and `ServerHttpRequest` so a filter can rewrite URI/headers while
  the route still reads `byteBody()`; `UriChangeAwareRequest` / `BodyChangeAwareRequest`
  let the runner detect in-place changes without diffing. `MethodFilter` (annotated
  filters) gets the same in-place-URI semantics, which is a behaviour change: a void
  `@RequestFilter` that changes the URI of its `MutableHttpRequest` argument now takes
  effect. `RequestLifecycle.findFiltersAfterRouteMatch` calls
  `router.findFilters(request, routeMatch)` so route and group filters are appended
  after the application filters, on the error path too.

### 8.4 Evaluation

Strengths:

- One execution model; routes declared in code are indistinguishable from controller
  routes for the rest of the framework.
- Several fixes with standalone value: immutable route infos (conditions are copied),
  filters applied per tier before ambiguity, per-request snapshots, the third
  specificity key, implicit HEAD routes for tables.
- The index and the compiled-matcher SPI are behaviour-preserving by construction and
  are exactly the performance work 5.3.x needs.
- Good hygiene: `@Experimental`, sealed public types, builders closed after declaration,
  `NoReflection` allowances documented, thirteen tested guide pages.

Risks:

- **Bundling.** Commit 6 also contains a general forms/upload/async-body feature
  (`io.micronaut.http.form`, `AsyncRequestBody`, `DefaultFormParts`, `UploadContent`,
  `StreamingUploadContent`, Netty binder changes, about 4.5k lines) that binds to
  controller arguments too. It must be reviewed on its own.
- **API surface.** 91 builder methods (the path-less overloads double the count) and a
  24-method filter matrix backed by 16 functional interfaces repeated on three specs.
  `executeOn` could be a modifier on the returned spec instead of a parameter on every
  method, and the `MutablePropagatedContext` variants could be a single two-argument
  overload. Trimming this is cheap before it is stable and impossible after.
- **Three parallel setting layers** (`RouteSpec`/`HttpRouteSpec` public,
  `HandlerUriRoute` internal, `DeclaredUriRoute` replaying setters as closures) and a
  430-line `LazyUriRouteInfo` delegator that must be edited whenever `RouteInfo` grows.
- **A `RouteTable` is a whole `DefaultRouter`** (own filter lists, status/error arrays,
  memoised suppliers); `RouteSourceRouter` re-implements every `Router` method to
  concatenate tiers. A lighter "URI route set" inside `DefaultRouter` would remove the
  decorator and the ordering fragility commit 5 had to fix.
- **Package cycle** between `io.micronaut.web.router` and `...router.builder`, which
  forces `RouteAssembly`, `RouteLocator`, `GroupErrorRoutes`, `RouteArguments` to be
  `public @Internal`.
- **Post-construction mutation** of `DefaultUrlRouteInfo` (`routeFilters`, `order`,
  `attributes`, `errorScope` are package-private non-final fields set "before the info
  is published").
- **Compatibility**: `@Inject` moves from `DefaultRouter(Collection<RouteBuilder>)` to a
  new two-argument constructor, so a `@Replaces(DefaultRouter.class)` subclass built on
  the old one silently loses all `HttpRoutes` beans. The `compareTo` change and the
  `MethodFilter` URI change are user-visible and need changelog entries.
- **GraalVM**: `HandlerMethod.getTargetMethod()` resolves `handle` on the lambda class
  reflectively; no reflect-config is added, so tooling that calls
  `ExecutableMethod.getTargetMethod()` on a handler route fails in a native image.
- **Semantics to settle**: group filters do not run for 404/405 under the group prefix;
  route `order` becomes a global tie-break that also applies to controllers; a locator
  can run twice per request (405 handling); async locators are not cancelled on
  disconnect and are invisible to CORS preflight.

### 8.5 Integration order into 5.3.x / 5.4

Each step is independently reviewable and leaves the tree releasable.

| Step | Content | Kind | Notes |
|------|---------|------|-------|
| 1 | `UriTemplateMatcher` keys (`getRequiredPrefix`, `getRawLength`, `getPathVariableCount`, `getPatternVariableCount`, `normalizeForMatching`) + third specificity key | perf + fix | behaviour change for `/t/{id}` vs `/t/{id:.+}`, document it; also cache the keys used by `resolveAmbiguity` (section 3.6 item 3) |
| 2 | `RouteIndex`, `IndexedRoute`, candidate-based `findInternal`/`findAny`, unified `allRoutesByMethod` | perf | provably equivalent; add a JMH benchmark next to `RequestArgumentSatisfierBenchmark` |
| 3 | `RouteDefinitions` + `AnnotatedMethodRouteBuilder` rewrite | cleanup | net code reduction |
| 4 | `RouteAssembly` extraction as a pure move, with `routeUri`/`routeCreated`/`addImplicitHeadRoutes` hooks | refactor | keeps `DefaultRouteBuilder` API |
| 5 | `Router.findAllClosest(request, Predicate)`, `FilteredRouter` delegation, `RouteOrders`, `RouteInfo.getAttributes`, `UriRouteInfo.getOrder` | API (default methods) | fixes the versioning slow path (section 3.5) |
| 6 | `RouteSource` / `RouteTable` / `RouteTableFactory.build(Consumer<RouteBuilder>)` / `RouteSourceRouter` (commits 1-5 squashed) | feature | decide whether a table stays a `DefaultRouter` or becomes a lighter route set first |
| 7 | Filter pipeline: `RouteFunctionFilter`, `GenericHttpFilter` factories, `MutableServerRequest` family, `MethodFilter` URI parity, `findFilters(request, routeMatch)` wiring incl. error path | feature + behaviour change | review the annotated-filter change explicitly |
| 8 | `HttpRoutes` core: builder minus locators/declared routes/forms, `HandlerMethod`, `HttpRoutesAssembly`, two-argument `DefaultRouter` constructor, `PathVariables` binding, groups + `RouteSpec`, `GroupErrorRoutes` hooks, guide pages | feature | trim the overload count and the filter matrix before landing; keep `@Inject` on the old constructor delegating to the new one |
| 9 | `RouteTableFactory.buildHttpRoutes` + "route tables" docs | feature | |
| 10 | Forms / `AsyncRequestBody` | separate feature | benefits controllers too |
| 11 | Declared routes, `CompiledRouteMatcher` SPI, `LazyUriRouteInfo`, proof module | feature | pairs with the compile-time route work in section 7 |
| 12 | Locators, synchronous first; async locators and `FilterRunner.doRouteMatchAsync` last | feature | settle cancellation and CORS preflight first |

Steps 1-5 are pure improvements to the existing design and could go into 5.3.x
patches; steps 6-12 are 5.4 features.

---

## 9. Recommended order of work

The list below merges the ranked items of sections 3 to 8 into one sequence. Each row is
a reviewable pull request; the "type" column says whether it changes behaviour.

| # | Work | Type | Sections |
|---|------|------|----------|
| 1 | Cache ambiguity keys on the route info; normalise the path once; index-based `UriTemplateMatcher.match`; port `UriTemplateMatcher` keys + third specificity key from the branch | perf | 3.6 (1, 3), 8.5 (1) |
| 2 | Port `RouteIndex` and candidate-based `findInternal` / `findAny`; exact-path map for static routes; no `RouteMatch` allocation on misses | perf | 3.6 (2, 4, 5), 8.5 (2) |
| 3 | Binding hot path: primitive re-conversion, `getRouteMatchInfo` / `getRouteWaitsFor` attribute lookups, `DefaultUnmatchedRequestArgumentBinder` streams, `BindingResult` value class | perf | 5.5 (2-5) |
| 4 | Executor: memoised streaming executor and `Scheduler`, conditional second `ServerHttpRequestContext`, `ResponseStrategy` switch | perf | 4.3 (1, 2) |
| 5 | Filters: precompiled patterns and method bitmask, no `Stream` in `checkOrdered`, `FilterOrder.Dynamic` resolved once, constant `BiFunction`, `CorsFilter` config cached | perf | 6.4 (1, 8) |
| 6 | Status routes indexed by code, error routes indexed and cached per exception class, `ExceptionHandler` lookup cached | perf | 3.6 (6) |
| 7 | `RouteDefinitions` + `RouteAssembly` extraction; `findAllClosest(request, Predicate)` and `FilteredRouter` delegation; precomputed route version | refactor + perf | 3.6 (8), 8.5 (3-5) |
| 8 | Per-route binding plan; `RouteMatch` split behind the existing facade; typed per-request server state | design | 5.5 (1, 8), 7.2-7.4 |
| 9 | Immutable `RouteTable` value + `RouteSource` SPI | feature | 7.1, 8.5 (6) |
| 10 | Filter chain as data, stateless server callbacks, decision and test for the around/response composition | design + behaviour | 6.4 (2, 3, 5, 6), 7.5 |
| 11 | Filter pipeline changes from the branch (`RouteFunctionFilter`, `MutableServerRequest`, URI-change parity) | feature + behaviour | 8.5 (7) |
| 12 | `HttpRoutes` builder core, then tables, forms, declared routes, locators | feature | 8.5 (8-12) |
| 13 | Compile-time route tables | feature | 7.6 |

Rows 1 to 6 are self-contained and measurable with the benchmarks described in 7.7.
Row 8 is the single change that most simplifies everything after it, because the binding
plan and the split match are what the builder, the tables and the compiled routes all
attach to.
