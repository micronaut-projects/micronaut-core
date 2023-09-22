/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.bench;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class SimpleControllerBenchmark {
    // run this benchmark with:
    // ./gradlew :test-suite-benchmarks:optimizedJmhJar && java -jar test-suite-benchmarks/build/libs/test-suite-benchmarks-jmh-all-*-SNAPSHOT.jar

    ApplicationContext ctx;

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(20)
    public byte[] getSomeJson() throws IOException {
        ctx = Micronaut.run(SimpleControllerBenchmark.class);
        try (InputStream s = ctx.getBean(EmbeddedServer.class).getURL().openStream()) {
            return s.readAllBytes();
        }
    }

    @TearDown
    public void shutdown() {
        // not part of the measurement
        ctx.close();
    }
}
