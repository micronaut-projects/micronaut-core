# Form-flow optimization: matched before/after

## Source and environment

- Baseline: `1100439ac44c9ac0ef8b1db66a6500afe4841f60`.
- Optimized production: `cffe18d5c127c69d1440e498a4d0156053699744` (#13813).
- Identical fixtures: `092ed67fea151df9cd89dde98ef2f62aae7ccdd3` (#13816).
- macOS 15.6, Apple M3 Max ARM64, OpenJDK 25.0.2, Parallel GC, 256 MiB heap.
- JMH: three forks, five one-second warmups and measurements, GC profiler.
- Builds and measurements ran sequentially, not concurrently.

The baseline needs a behavior-preserving extraction of its inline collection
body to package-private `transformForm` for the matched fixture. No fast paths,
volatile-reference changes or decoder-failure cleanup were backported. This is
not a byte-identical untouched baseline. First-item fixtures need no such seam.

## Results

| Native fixture | Baseline ns/op | Optimized ns/op | Baseline → optimized B/op |
| --- | ---: | ---: | ---: |
| First mapped item | 19.75 ± 0.10 | 15.52 ± 0.12 | 216 → 144 |
| First completed item | 24.03 ± 0.17 | 15.90 ± 0.14 | 248 → 152 |
| Immediate form, 1 field | 353.9 ± 16.7 | 306.1 ± 16.6 | 2598 → 2286 |
| Immediate form, 16 fields | 1786.0 ± 41.9 | 1615.6 ± 125.4 | 8927 → 8796 |
| Scheduled form, 1 field | 6438.5 ± 256.2 | 6512.5 ± 72.7 | 2802 → 2782 |
| Scheduled form, 16 fields | 8952.3 ± 813.0 | 8539.4 ± 128.0 | 9173 → 9542 |

First-item fixtures improve about 21%/34%, saving 72/96 B/op. Immediate
single-field aggregation improves about 13.5%, saving 312 B/op, with
non-overlapping reported timing intervals. Treat larger/scheduled timings as
inconclusive; scheduled 16-field allocation was directionally worse. Scheduled
means decoder subscription, not independently delayed field buffering.

These before/after native cases are the primary optimization evidence. Reactor
controls are useful drift checks, not a replacement for before/after measurement.

## Real HTTP negative control

Loopback HTTP/1.1 completed multipart uploads: 16 KiB, fast consumer, two forks,
three one-second warmups and four measurements. Fixtures verify byte count and
memory/disk storage selection; controllers close uploads and input streams.

| Callers / storage | Baseline µs/op | Optimized µs/op | Baseline → optimized B/op |
| --- | ---: | ---: | ---: |
| 1 / memory | 164.3 ± 1.4 | 164.3 ± 1.9 | 50,727 → 50,548 |
| 1 / mixed disk spill | 777.8 ± 274.0 | 940.1 ± 195.6 | 69,511 → 73,736 |
| 4 / memory | 245.2 ± 2.8 | 246.0 ± 2.9 | 51,843 → 51,558 |
| 4 / mixed disk spill | 884.2 ± 43.2 | 891.5 ± 57.3 | 57,084 → 56,851 |

No measurable memory-upload improvement. Disk intervals overlap, especially for
one caller; do not claim a timing win or regression. This multipart route is
not the buffered full-form transformation, so it is a negative control for a
blanket HTTP claim, not a sensitive integration test of every changed fast path.
No exhaustive temp-file leak/count assertion was added.

## Async-profiler

Async-profiler 4.5 attached locally without sudo or global installation. Matched
first-completed-flow CPU flamegraphs contain the relevant paths. Baseline
allocation stacks contain the separate subscriber, `FirstFuture`,
`AtomicReference` and `AtomicBoolean`. Optimized captures retain the direct
`FirstFuture` subtype and `AtomicBoolean`, with no sampled `AtomicReference`
allocation stack. Independent JMH GC results quantify the net reduction:
248 → 152 B/op (39% fewer bytes).

Profiles explain wrapper removal, not every individual byte. CPU profiles used
no-fork diagnostic JMH runs, which warn about JVM/Blackhole differences; no
comparative CPU percentages or timing claims are derived from those captures.
Profiler runs are separate from the measurement results above. No HTTP workload
was profiled.

## Reproduction

Prepare isolated worktrees at the source refs above, copy identical benchmark
fixtures to each, and extract only the baseline method seam described above.
In each worktree, build before measurement:

```sh
MICRONAUT_SERVER_PORT=-1 ./gradlew --offline \
  :micronaut-http-server:checkstyleMain :benchmarks:jmhJar
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*FormFieldFlowsBenchmark.(reactorFirst|flowFirst|reactorFirstCompleted|flowFirstCompleted)' \
  -f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' -rf json -rff first.json
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*FormAggregationBenchmark.aggregate' -p implementation=native,reactor \
  -p fields=1,16 -p delayed=false,true -f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' -rf json -rff forms.json
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*MultipartHttpBenchmark.upload' -p storage=memory,mixed -p size=16384 \
  -p consumer=fast -t 1 -f 2 -wi 3 -i 4 -w 1s -r 1s -prof gc \
  -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC' -rf json -rff http.json
```

Repeat HTTP with `-t 4`. For diagnostic profiles, start the following workload
and attach to its JMH PID during measurement, once per side and profile event:

```sh
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*FormFieldFlowsBenchmark.flowFirstCompleted' -f 0 -wi 10 -i 40 \
  -w 1s -r 1s -jvmArgs '-Xms256m -Xmx256m -XX:+UseParallelGC'
asprof -e cpu -d 12 -f cpu-first.html <PID>
asprof -e alloc --alloc 1m -d 12 -o collapsed -f alloc-first.collapsed <PID>
```

Both isolated builds passed. Fixture setup checks empty/single/repeated/16-field
parity in immediate and scheduled modes. Benchmark checks do not substitute for
production cancellation/context/ownership regression tests.

Retain provisionally for allocation and directly affected fixture gains, not a
general HTTP speed claim. Evidence is from one machine/JDK, in-memory prebuilt
form parts, a limited HTTP matrix and sampled allocation profiles. Raw JSON,
logs, flamegraphs and collapsed stacks remain local under the analysis session's
`form-flow-analysis/artifacts` directory; no special workflow was added.
