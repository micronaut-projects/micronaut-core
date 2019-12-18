package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.server.binding.TestController;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class StartupBenchmark {

    @Benchmark
    public void startup() {
        try (ApplicationContext context = ApplicationContext.run()) {
            final TestController controller =
                    context.getBean(TestController.class);
        }
    }
}
