# Buffered-form fast paths

See [matched before/after investigation](BINDER-BEFORE-AFTER.md) for subsequent
native baseline comparisons, real HTTP controls and async-profiler evidence.

Production changes belong to #13813; these benchmark fixtures and measurements
belong to #13816. No benchmark workflow or performance gate is added.

## Decision

Retain terminal-flow fast paths and reduce publication-only wrapper allocation:

- `FirstFuture` is also the subscriber, removing a subscriber/publication wrapper.
- Subscription and running-flow references use volatile publication. Compound
  first-item/terminal arbitration still uses CAS.
- Terminal `firstFlatMap` and `Concat` flows use `tryComplete` directly; pending
  flows still register observation before cancellation can reach them.
- A synchronously collected buffered form uses imperative completion before
  conversion. Pending decoders retain asynchronous completion and cancellation.
- Decoder creation failure closes buffered bytes before publisher handoff.

Do not retain the previous concurrent multipart prototype in production. These
changes do not alter multipart concurrency, eager draining, disk scheduling,
request context, released API defaults or foreign-publisher discard hooks.

## Matched controls and limitations

`FormFieldFlowsBenchmark` compares first-item pipelines with the Reactor controls
in that fixture. `FormAggregationBenchmark` compares production transformation
with no-prefetch Reactor `concatMap`/`then` using matching reader lookup,
conversion, bound request context, fixture construction and future/cancel
adapters. Setup checks empty, single, repeated and 16-field parity for immediate
and scheduled decoder sources.

Fields contain available bodies; the scheduled mode schedules decoder
subscription, not independently delayed field buffering. Neither fixture
measures HTTP parsing, disk-backed uploads or route execution. Do not infer an
end-to-end request speedup. Timing gains require non-overlapping reported JMH
error ranges; lower allocation alone does not establish a latency win.

The separate prototype session measured 144/152 B/op for mapped/completed first
items, versus its Reactor controls at 208/304 B/op. Its longer immediate-form
comparison found a single-field timing win, but inconclusive 16-field and
scheduled-source timings. Those are historical prototype observations, not
measurements of the current PR head.

## Reproduction on the current stack

Fresh matched runs on OpenJDK 25.0.2, Parallel GC, 256 MiB heap, GC profiler
and three forks produced:

| Fixture | Native ns/op | Reactor ns/op | Native / Reactor B/op |
| --- | ---: | ---: | ---: |
| First mapped item | 15.69 ± 0.14 | 18.17 ± 0.13 | 144 / 208 |
| First completed item | 15.83 ± 0.34 | 34.24 ± 0.23 | 152 / 304 |
| Immediate form, 1 field | 298.45 ± 6.45 | 350.87 ± 15.84 | 2283 / 2641 |
| Immediate form, 16 fields | 1665.98 ± 34.02 | 1685.92 ± 32.88 | 8773 / 9223 |

First-item fixtures are about 14%/54% faster than their Reactor controls with
31%/50% less allocation. The immediate single-field form is about 15% faster
with 14% less allocation. Sixteen-field timing is **inconclusive** (overlapping
error ranges); allocation is about 5% lower. These are fixture comparisons with
Reactor, not a before/after HTTP benchmark. Parity checks pass in both immediate
and scheduled modes; no fresh scheduled-source timing claim is made.

First-item runs use five one-second warmups and five measurements; whole-form
runs use six of each. Local raw files: `prototype-current-first.json` and
`prototype-current-form.json` in the session's parent directory.

```sh
MICRONAUT_SERVER_PORT=-1 ./gradlew --offline \
  :micronaut-http-server:test :micronaut-http-server:checkstyleMain \
  :benchmarks:jmhJar
java -cp benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  io.micronaut.http.server.binding.FormAggregationBenchmark
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*FormFieldFlowsBenchmark.(reactorFirst|flowFirst|reactorFirstCompleted|flowFirstCompleted)' \
  -f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' \
  -rf json -rff benchmarks/build/first-current.json
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*FormAggregationBenchmark.aggregate' -p delayed=false \
  -f 3 -wi 6 -i 6 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' \
  -rf json -rff benchmarks/build/form-current.json
```

Use identical JVM, GC, heap and machine conditions for before/after runs. Do not
run other builds or benchmarks concurrently with JMH. Raw results remain local.
