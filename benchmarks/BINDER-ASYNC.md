# Async binder experiments

## Changes retained

Buffered full-form binding awaits delayed decoding without blocking. Future and single-result
publisher binding share the same completion path. Cancellation now reaches the decoder and
discards queued foreign-publisher fields. A field already being buffered may still finish:
`ExecutionFlow.cancel()` is only a hint for full-body buffering. The completion mapping closes
its available body, and cancellation prevents final conversion/caching. No body limits are
bypassed. Conversion runs under the bound request context, including when decoding completes
on another thread.

The collected map stores a string for a single field and allocates a list only for repeated
names. This avoids a second map and per-field completion entries/lists.

## Concurrent completion: not retained in production

`ConcurrentFormPublisher` is a **benchmark-only prototype**, not used by any binder. It was
tested during the investigation for out-of-order delayed completion, bounded demand,
cancellation of running flows, release of queued values, empty completions, fail-fast errors,
foreign-source context and 100,000 immediate completions without recursive request growth.
Those focused tests are not proof of full Reactor equivalence or a Reactive Streams TCK pass.

The 16-immediately-completed-field fixture showed a substantial regression:

| Pipeline | ns/op (JMH error) | B/op |
|---|---:|---:|
| Existing Reactor flatMap | 199.82 ± 1.49 | 592 |
| Native concurrent prototype | 1312.34 ± 17.50 | 2048 |

Consequently, multipart and completed-part publisher binders retain their existing flatMap,
eager `bufferNow` draining and disk-executor conversion. Applying this prototype there would
add overhead without a demonstrated benefit. No upload transfer API or hot Mono semantics
were changed.

## Buffered-form transformation

`BufferedFormBindingBenchmark` reconstructs the previous synchronous transformation policy
(immediate field collection, buffered field-to-text mapping and conversion). The native case
calls the shared binder transformation. Both include fixture construction, field creation,
buffered-byte handoff and conversion; neither measures actual form-byte parsing or transport.
This is a controlled policy comparison, **not an old-release checkout comparison**. The
delayed case schedules decoding onto a single executor; the old implementation rejected it,
so there is no old delayed baseline.

| Policy | Fields | ns/op (JMH error) | B/op |
|---|---:|---:|---:|
| Previous synchronous | 1 | 277.62 ± 12.22 | 2337 |
| Native immediate | 1 | 326.84 ± 12.68 | 2470 |
| Previous synchronous | 16 | 1958.49 ± 123.99 | 10033 |
| Native immediate | 16 | 1695.97 ± 54.53 | 9518 |
| Native delayed | 1 | 6707.98 ± 203.37 | 2930 |
| Native delayed | 16 | 8910.24 ± 249.95 | 9874 |

The native fixture was about 13% faster with 5% fewer allocated bytes at 16 fields, but about
18% slower at one field. Its primary benefit is correctness for asynchronous decoders, not
a blanket upload-performance improvement. No end-to-end or disk-spill speedup is claimed.

## Reproduce locally

Build with `./gradlew --offline :benchmarks:jmhJar` (drop `--offline` if dependencies are not cached).
Run benchmarks after tests have finished, without other benchmark/build work in parallel:

```sh
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*BufferedFormBindingBenchmark.*' \
  -f 2 -wi 4 -i 5 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' \
  -rf json -rff buffered-form.json

java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*FormFieldFlowsBenchmark.*ConcurrentCompleted' \
  -f 2 -wi 4 -i 5 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' \
  -rf json -rff concurrent-form.json
```

These results were collected locally on macOS using two JVM forks per configuration. No
special GitHub Actions or performance gates were added. Future concurrent implementations
need differential testing for scalar ordering, source discard hooks, cancellation races,
context, executor rejection, disk-backed parts and form deadlocks before use in production.

## Reactor-absent runtime probe

```sh
./gradlew --offline -I http-server/src/test/no-reactor.init.gradle \
  :micronaut-http-server:reactorAbsentBinderProbe
```

This compiles normally and launches a separate JVM with Reactor jars removed from the test
runtime classpath. The probe asserts that `Mono.class` is absent and exercises JSON future
binding and full-form future binding from a native Reactive Streams publisher. It bypasses
JUnit discovery, whose other test signatures themselves refer to Reactor. Both paths pass.
This is not validation of the whole Netty transport or every publisher-facing API without
Reactor.

The form probe initially failed in `ReactorInterop.subscribe` because an unconditional
`instanceof CorePublisher` linked Reactor even for a plain native publisher. The interop
edge now checks optional Reactor linkage once, without reflective class loading. Existing
Reactor source context/discard support is retained when the library is present.

## End-to-end loopback multipart uploads

`MultipartHttpBenchmark` exercises the completed-upload binder through a real HTTP/1.1
connection using the JDK HTTP client. Each request's response verifies its consumed size and
actual memory/disk storage. Mixed storage uses a 1 KiB spill threshold. The slow consumer
reads 4 KiB at a time and pauses for 100 microseconds per read on a blocking executor; it
does not simulate a slow network sender or a streaming-part subscriber.

Two forks per configuration, 3 warmup and 4 measurement iterations of 1 second each, tested
all eight configurations with 1 and 4 concurrent JMH callers. These are **current-path
baselines, not before/after speedups**. GC allocation profiling includes the client, server
and storage plumbing in the same JVM. Some latency error intervals are wide: e.g. serial
16 KiB fast mixed storage was 1093 ± 313 microseconds, and concurrent 256 KiB fast mixed
storage was 2115 ± 737 microseconds. These measurements do not establish small differences.

| Consumer | Size | Storage | 1 caller, µs/op | 4 callers, µs/op |
|---|---:|---|---:|---:|
| Fast | 16 KiB | Memory | 193 | 246 |
| Fast | 16 KiB | Mixed/disk | 1093 | 825 |
| Fast | 256 KiB | Memory | 650 | 1365 |
| Fast | 256 KiB | Mixed/disk | 1440 | 2115 |
| Slow | 16 KiB | Memory | 914 | 759 |
| Slow | 16 KiB | Mixed/disk | 1680 | 1739 |
| Slow | 256 KiB | Memory | 11446 | 11545 |
| Slow | 256 KiB | Mixed/disk | 12245 | 13353 |

After building the JMH jar, run these commands separately (not concurrently):

```sh
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*MultipartHttpBenchmark.upload' -t 1 \
  -f 2 -wi 3 -i 4 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' \
  -rf json -rff multipart-http-serial.json

java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*MultipartHttpBenchmark.upload' -t 4 \
  -f 2 -wi 3 -i 4 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' \
  -rf json -rff multipart-http-concurrent.json
```
