package io.micronaut.http.server;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.web.router.Router;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
public class DefaultMicronautHttpHandler implements MicronautHttpHandler,
    MicronautAsyncHttpHandler {

    private final RouteExecutor routeExecutor;
    private final Router router;
    private final ErrorResponseProcessor<?> errorResponseProcessor;

    public DefaultMicronautHttpHandler(final RouteExecutor routeExecutor,
                                       final Router router,
                                       final ErrorResponseProcessor<?> errorResponseProcessor) {
        this.routeExecutor = routeExecutor;
        this.router = router;
        this.errorResponseProcessor = errorResponseProcessor;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> handleAsync(final HttpRequest<?> request) {
        RequestLifecycle requestLifecycle = new RequestLifecycle(routeExecutor, request);

        return ReactiveExecutionFlow
            .fromFlow(requestLifecycle.normalFlow())
            .toPublisher();
    }

    @Override
    public HttpResponse<?> handle(final HttpRequest<?> request) {
        return Mono.from(handleAsync(request))
            .block();
    }
}
