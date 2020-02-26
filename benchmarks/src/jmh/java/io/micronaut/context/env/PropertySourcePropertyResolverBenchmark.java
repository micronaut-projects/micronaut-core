/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
