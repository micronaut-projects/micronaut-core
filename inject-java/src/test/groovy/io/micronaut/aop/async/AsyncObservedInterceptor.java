package io.micronaut.aop.async;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the result type and when the asynchronous action completed.
 */
@Singleton
@InterceptorBean(AsyncObserved.class)
public class AsyncObservedInterceptor implements MethodInterceptor<Object, Object> {

    final Map<String, InterceptedMethod.ResultType> resultTypes = new ConcurrentHashMap<>();
    final List<String> completed = new CopyOnWriteArrayList<>();

    private final ConversionService conversionService;

    public AsyncObservedInterceptor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String name = context.getMethodName();
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        resultTypes.put(name, interceptedMethod.resultType());
        try {
            return switch (interceptedMethod.resultType()) {
                case PUBLISHER -> interceptedMethod.handleResult(
                    Flux.from(interceptedMethod.interceptResultAsPublisher()).doFinally(s -> completed.add(name))
                );
                case COMPLETION_STAGE -> interceptedMethod.handleResult(
                    interceptedMethod.interceptResultAsCompletionStage().whenComplete((v, t) -> completed.add(name))
                );
                case SYNCHRONOUS -> {
                    try {
                        yield interceptedMethod.handleResult(interceptedMethod.interceptResult());
                    } finally {
                        completed.add(name);
                    }
                }
            };
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }
}
