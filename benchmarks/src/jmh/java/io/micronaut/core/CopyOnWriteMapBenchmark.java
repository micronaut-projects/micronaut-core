package io.micronaut.core;

import io.micronaut.core.util.CopyOnWriteMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CopyOnWriteMapBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(CopyOnWriteMapBenchmark.class.getName() + ".*")
            .warmupIterations(3)
            .measurementIterations(5)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            //.addProfiler(LinuxPerfAsmProfiler.class)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public String get(S s) {
        return s.map.get("foo");
    }

    @Benchmark
    public String computeIfAbsent(S s) {
        return s.map.computeIfAbsent("fizz", s.ciaUpdate);
    }

    @Benchmark
    public String getWithCheck(S s) {
        String v = s.map.get("fizz");
        if (v == null) {
            return s.map.computeIfAbsent("fizz", s.ciaUpdate);
        } else {
            return v;
        }
    }

    @State(Scope.Thread)
    public static class S {
        @Param({"CHM", "COW"})
        Type type;
        @Param({"1", "2", "5", "10"})
        int load;
        private Map<String, String> map;
        private Function<String, String> ciaUpdate;

        @Setup
        public void setUp() {
            map = switch (type) {
                case CHM -> new ConcurrentHashMap<>(16, 0.75f, 1);
                case COW -> CopyOnWriteMap.create(16);
            };
            // doesn't really stress the collision avoidance algorithm but oh well
            map.put("foo", "bar");
            for (int i = 0; i < load; i++) {
                map.put("f" + i, "b" + i);
            }
            ciaUpdate = m -> "buzz" + m;
        }
    }

    public enum Type {
        CHM,
        COW,
    }
}
