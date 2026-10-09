# Available-body rebuffering

The generic and Netty `toStreaming` paths now hand an already-available owned
`ReadBuffer` to the destination shared buffer, without creating a publisher,
adapter, subscriber and subscription to transfer one item. Streaming sources
continue to use the original backpressure/cancellation/context bridge. Length
validation and trailer completion remain in the shared buffer.

An independent correctness fix reads expected length before claiming the source.
The available-body regression fails on the previous generic implementation.

## Matched fixture

`AvailableBodyRebufferBenchmark` compares the direct path with the previous
publisher-adapter implementation, with the length-before-claim defect corrected
in both. Both create an available body, convert it, buffer it, observe its length
and close all owned bodies. Both include the payload array allocation.

Local environment: macOS, OpenJDK 25.0.2+10-69. Two forks, three one-second
warmups, four one-second measurements per fork, GC profiler.

| Bytes | Direct ops/s | Adapter ops/s | Direct B/op | Adapter B/op |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 8,372,388 | 7,653,573 | 776.50 | 891.24 |
| 128 | 8,701,541 | 7,096,848 | 904.43 | 1,019.23 |
| 8192 | 2,839,976 | 2,229,736 | 8,968.51 | 9,083.29 |

Allocation is about 115 B/op lower throughout. Throughput is directionally
9–27% higher; the zero-byte and 8192-byte confidence intervals overlap, so these
are not statistically established improvements. The 128-byte intervals do not
overlap. This fixture measures default-factory rebuffering, not end-to-end HTTP
or Netty throughput.

```sh
./gradlew :benchmarks:jmhJar
java -jar benchmarks/build/libs/benchmarks-5.3.0-SNAPSHOT-jmh.jar \
  '.*AvailableBodyRebufferBenchmark.*' \
  -f 2 -wi 3 -i 4 -w 1s -r 1s -prof gc \
  -rf json -rff available-body-rebuffering.json
```

## Reactor boundaries

- Native available-body rebuffering needs no Reactor operators.
- Native streaming publishers keep the existing optional Reactor context/discard
  bridge for foreign Reactor sources; removing it would change ownership and
  cancellation semantics.
- Stored upload cleanup uses that optional bridge for non-blocking-thread
  detection. With Reactor present it delegates to `Schedulers`, preserving both
  marker threads and registered predicates; without Reactor it requires no
  scheduler linkage.
- Released publisher-based form binders and route execution retain their Reactor
  operators and scheduling/context contracts. They are compatibility paths, not
  redundant buffering to remove in this change.

No released API/default is removed, no blocking body-elements API is added, and
no custom GitHub Actions workflow is required. Classloader tests cover absent
Reactor; a new native-image execution has not yet been performed for the added
scheduler bridge.
