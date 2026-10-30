package io.micronaut.scheduling.async;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextConfiguration;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncContextPropagationTest {

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = {"thread-local", "scoped-value"})
    void asyncMethodsRunWithTheCallersContext(String mode) throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "AsyncContextPropagationTest",
            "micronaut.propagation", mode,
            "micronaut.executors.async-pool.type", "fixed",
            "micronaut.executors.async-pool.number-of-threads", 1
        ))) {
            AsyncService service = context.getBean(AsyncService.class);
            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new TestElement("value"));

            CompletableFuture<String> fromVoid = new CompletableFuture<>();
            propagatedContext.propagate(() -> service.onScheduled(fromVoid));
            assertEquals("value", fromVoid.get(5, TimeUnit.SECONDS));

            CompletionStage<String> fromStage = propagatedContext.propagate(service::onNamedExecutor);
            assertEquals("value", fromStage.toCompletableFuture().get(5, TimeUnit.SECONDS));

            // the single worker thread doesn't keep the context
            assertEquals("none", service.onNamedExecutor().toCompletableFuture().get(5, TimeUnit.SECONDS));
        }
    }

    private static String currentValue() {
        return PropagatedContext.find()
            .flatMap(ctx -> ctx.find(TestElement.class))
            .map(TestElement::value)
            .orElse("none");
    }

    @Singleton
    @Requires(property = "spec.name", value = "AsyncContextPropagationTest")
    static class AsyncService {

        @Async
        void onScheduled(CompletableFuture<String> result) {
            result.complete(currentValue());
        }

        @Async("async-pool")
        CompletionStage<String> onNamedExecutor() {
            return CompletableFuture.completedFuture(currentValue());
        }
    }

    record TestElement(String value) implements PropagatedContextElement {
    }
}
