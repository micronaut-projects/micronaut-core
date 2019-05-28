package io.micronaut.context.env;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;

@State(Scope.Benchmark)
public class PropertySourcePropertyResolverBenchmark {

    Map<String, String> props = new HashMap<>();

    @Setup
    public void prepare() {
        for (int i = 0; i < 600; i++) {
             props.put(i + "}_A_B_C_D_E_F_G_SERVICE_PORT", "foo");
        }
    }

    @Benchmark
    public void benchmarkPropertyResolverConstruction() {
        new PropertySourcePropertyResolver(new EnvironmentPropertySource(props));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + PropertySourcePropertyResolverBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }

    class EnvironmentPropertySource extends MapPropertySource {

        EnvironmentPropertySource(Map map) {
            super("env", map);
        }

        @Override
        public PropertyConvention getConvention() {
            return PropertyConvention.ENVIRONMENT_VARIABLE;
        }
    }
}
