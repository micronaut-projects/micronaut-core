/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.retry.intercept;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.AnnotationValue;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.CircuitBreakerWindow;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.RetryRegistry;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * A {@link MethodInterceptor} that retries an operation according to the specified
 * {@link Retryable} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class DefaultRetryInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryInterceptor.class);
    private static final String NAME = "name";
    private final ConversionService conversionService;
    @Nullable
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledExecutorService executorService;
    /**
     * The circuit breaker states of the methods, by the method and the annotations its circuit
     * and its policy are resolved from: methods of different beans can share an
     * {@link ExecutableMethod} that is equal, e.g. one inherited from a common superclass, and
     * name different circuits or declare different policies on their classes.
     */
    private final Map<CircuitKey, CircuitBreakerRetry> circuitContexts = new ConcurrentHashMap<>();
    private final DefaultRetryRunner retryRunner;
    @Nullable
    private final RetryRegistry retryRegistry;
    /**
     * The policies of the methods with a named policy, by their {@code @Retryable} value, from
     * which alone the policy is built: methods of different beans can share an
     * {@link ExecutableMethod} that is equal, e.g. one inherited from a common superclass.
     */
    private final Map<AnnotationValue<Retryable>, RetryPolicy> namedPolicies = new ConcurrentHashMap<>();
    private final NamedCircuits namedCircuits;

    /**
     * Construct a default retry method interceptor with the event publisher.
     *
     * @param conversionService The conversion service
     * @param eventPublisher The event publisher to publish retry events
     * @param executorService The executor service to use for completable futures
     */
    public DefaultRetryInterceptor(ConversionService conversionService,
                                   @Nullable
                                   ApplicationEventPublisher eventPublisher,
                                   @Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this(conversionService, eventPublisher, executorService, null, null, null);
    }

    /**
     * Construct a default retry method interceptor with the event publisher and the registry of
     * the named retry policies, which the methods annotated {@code @Retryable(name = "...")} use,
     * and of the named circuit breakers, which the methods annotated
     * {@code @CircuitBreaker(name = "...")} share.
     *
     * @param conversionService The conversion service
     * @param eventPublisher The event publisher to publish retry events
     * @param executorService The executor service to use for completable futures
     * @param retryRegistry The registry of the named retry policies
     * @param namedCircuits The circuits of the named circuit breakers
     * @since 5.3.0
     */
    public DefaultRetryInterceptor(ConversionService conversionService,
                                   @Nullable
                                   ApplicationEventPublisher eventPublisher,
                                   @Named(TaskExecutors.SCHEDULED) ExecutorService executorService,
                                   @Nullable RetryRegistry retryRegistry,
                                   @Nullable NamedCircuits namedCircuits) {
        this(conversionService, eventPublisher, executorService, retryRegistry, namedCircuits, null);
    }

    /**
     * Construct a default retry method interceptor with the event publisher, the registry of the
     * named retry policies and the named circuit breakers, in a context whose circuit breaker methods
     * are watched: the circuit of a method that went, or came back in a new generation, is dropped.
     *
     * @param conversionService The conversion service
     * @param eventPublisher The event publisher to publish retry events
     * @param executorService The executor service to use for completable futures
     * @param retryRegistry The registry of the named retry policies
     * @param namedCircuits The circuits of the named circuit breakers
     * @param beanContext The bean context
     * @since 5.3.0
     */
    @Inject
    public DefaultRetryInterceptor(ConversionService conversionService,
                                   @Nullable
                                   ApplicationEventPublisher eventPublisher,
                                   @Named(TaskExecutors.SCHEDULED) ExecutorService executorService,
                                   @Nullable RetryRegistry retryRegistry,
                                   @Nullable NamedCircuits namedCircuits,
                                   @Nullable BeanContext beanContext) {
        this.retryRegistry = retryRegistry;
        this.conversionService = conversionService;
        this.eventPublisher = eventPublisher;
        this.executorService = (ScheduledExecutorService) executorService;
        this.retryRunner = new DefaultRetryRunner(this.executorService, this::sleep);
        this.namedCircuits = namedCircuits == null ? new NamedCircuits() : namedCircuits;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.watchMethods(CircuitBreaker.class, change -> {
                // a circuit belongs to the method it guards: a method that went, or came back changed, starts closed;
                // a named circuit is shared by name and outlives any one method
                for (ExecutableMethodChange.Entry<CircuitBreaker> gone : change.removed()) {
                    circuitContexts.keySet().removeIf(key -> key.method().equals(gone.method()));
                }
            });
        }
    }

    /**
     * @return How many circuit breaker methods hold a circuit
     */
    @Internal
    public int circuitContexts() {
        return circuitContexts.size();
    }

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition();
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<AnnotationValue<Retryable>> opt = context.findAnnotation(Retryable.class);
        if (opt.isEmpty()) {
            return context.proceed();
        }

        AnnotationValue<Retryable> retry = opt.get();
        boolean isCircuitBreaker = context.hasStereotype(CircuitBreaker.class);
        MutableRetryState retryState;
        AnnotationRetryStateBuilder annotationRetryStateBuilder = new AnnotationRetryStateBuilder(
            context
        );
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);

        String name = retry.stringValue(NAME).orElse("");
        RetryPolicy namedPolicy = null;
        if (!name.isEmpty()) {
            try {
                namedPolicy = namedPolicies.computeIfAbsent(
                    retry,
                    value -> annotationRetryStateBuilder.retryPolicy(namedPolicy(name, context))
                );
            } catch (RuntimeException e) {
                return interceptedMethod.handleException(e);
            }
        }

        // a circuit with a rolling window takes the permit of a publisher when it is subscribed
        CircuitBreakerRetry windowedCircuit = null;
        if (isCircuitBreaker) {
            RetryPolicy retryPolicy = namedPolicy;
            AnnotationValue<CircuitBreaker> circuitBreaker = context.findAnnotation(CircuitBreaker.class).orElse(null);
            CircuitBreakerRetry circuitBreakerRetry;
            try {
                circuitBreakerRetry = circuitContexts.computeIfAbsent(
                    new CircuitKey(context.getTarget().getClass(), context.getExecutableMethod(), retry, circuitBreaker),
                    key -> circuitBreakerRetry(context, annotationRetryStateBuilder, retryPolicy, circuitBreaker)
                );
            } catch (RuntimeException e) {
                return interceptedMethod.handleException(e);
            }
            retryState = circuitBreakerRetry.newInvocation();
            if (circuitBreakerRetry.circuit().getWindow() != null) {
                windowedCircuit = circuitBreakerRetry;
            }
        } else if (namedPolicy == null) {
            retryState = (MutableRetryState) annotationRetryStateBuilder.build();
        } else {
            retryState = (MutableRetryState) new PolicyRetryStateBuilder(namedPolicy).build();
        }

        MutableConvertibleValues<Object> attrs = context.getAttributes();
        attrs.put(RetryState.class.getName(), retry);

        try {
            if (windowedCircuit == null || interceptedMethod.resultType() != InterceptedMethod.ResultType.PUBLISHER) {
                retryState.open();
            }
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    Supplier<Publisher<Object>> supplier = () -> (Publisher<Object>) interceptedMethod.interceptResult(this);
                    RetryEventEmitter retryEventEmitter = (state, exception) -> publishRetryEvent(context, state, exception);
                    Publisher<Object> publisher;
                    if (windowedCircuit == null) {
                        publisher = retryRunner.executePublisher(supplier, retryState, context.toString(), retryEventEmitter);
                    } else {
                        CircuitBreakerRetry circuit = windowedCircuit;
                        publisher = Flux.defer(() -> {
                            MutableRetryState invocation = circuit.newInvocation();
                            invocation.open();
                            return retryRunner.executePublisher(supplier, invocation, context.toString(), retryEventEmitter);
                        });
                    }
                    return interceptedMethod.handleResult(publisher);
                }
                case COMPLETION_STAGE -> {
                    return interceptedMethod.handleResult(
                        retryRunner.executeCompletionStage(
                            () -> interceptedMethod.interceptResultAsCompletionStage(this),
                            retryState,
                            context.toString(),
                            (state, exception) -> publishRetryEvent(context, state, exception)
                        )
                    );
                }
                case SYNCHRONOUS -> {
                    return retryRunner.executeSync(
                        () -> (Object) interceptedMethod.interceptResult(this),
                        retryState,
                        context.toString(),
                        (state, exception) -> publishRetryEvent(context, state, exception)
                    );
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private RetryPolicy namedPolicy(String name, MethodInvocationContext<Object, Object> context) {
        if (retryRegistry == null) {
            throw new IllegalStateException("No RetryRegistry for the retry policy [" + name + "] of " + context);
        }
        try {
            return retryRegistry.getPolicy(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage() + ", used by @Retryable(name = \"" + name + "\") of " + context, e);
        }
    }

    /**
     * The circuit breaker state of a method: its policy is resolved once, and a method with a
     * name joins the circuit of the name.
     */
    private CircuitBreakerRetry circuitBreakerRetry(MethodInvocationContext<Object, Object> context,
                                                    AnnotationRetryStateBuilder annotationRetryStateBuilder,
                                                    @Nullable RetryPolicy namedPolicy,
                                                    @Nullable AnnotationValue<CircuitBreaker> circuitBreaker) {
        CircuitBreakerPolicy circuitBreakerPolicy = namedPolicy == null
            ? annotationRetryStateBuilder.circuitBreakerPolicy()
            : annotationRetryStateBuilder.circuitBreakerPolicy(namedPolicy);
        CircuitBreakerWindow window = annotationRetryStateBuilder.circuitBreakerWindow();
        String name = circuitBreaker == null ? "" : circuitBreaker.stringValue("name").orElse("");
        CircuitBreakerRetry.Circuit circuit;
        if (circuitBreaker == null || name.isEmpty()) {
            circuit = new CircuitBreakerRetry.Circuit(circuitBreakerPolicy.getResetTimeout().toMillis(), window);
        } else {
            boolean declaresReset = circuitBreaker.contains("reset");
            String user = "@CircuitBreaker of " + context.getDeclaringType().getName() + "#" + context.getMethodName();
            circuit = namedCircuits.join(name, circuitBreakerPolicy, window, declaresReset, user);
        }
        return new CircuitBreakerRetry(
            circuit,
            new PolicyRetryStateBuilder(circuitBreakerPolicy.asRetryPolicy()),
            context,
            eventPublisher,
            circuitBreakerPolicy.isThrowWrappedException()
        );
    }

    private void publishRetryEvent(MethodInvocationContext<Object, Object> context,
                                   MutableRetryState retryState,
                                   Throwable exception) {
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new RetryEvent(context, retryState, exception));
            } catch (Exception eventException) {
                LOG.error("Error occurred publishing RetryEvent: {}", eventException.getMessage(), eventException);
            }
        }
    }

    /**
     * Performs the sleep between retries and can be overridden to customize sleep behavior.
     *
     * @param delayMillis The delay in milliseconds
     * @throws InterruptedException If the thread is interrupted during sleep
     */
    protected void sleep(long delayMillis) throws InterruptedException {
        Thread.sleep(delayMillis);
    }

    /**
     * The key of the circuit breaker state of a method and bean.
     *
     * @param targetType The class of the intercepted target
     * @param method The method
     * @param retry The {@code @Retryable} value of the method, from which its policy is resolved
     * @param circuitBreaker The {@code @CircuitBreaker} value of the method, from which its circuit is resolved
     */
    private record CircuitKey(Class<?> targetType,
                              ExecutableMethod<?, ?> method,
                              AnnotationValue<Retryable> retry,
                              @Nullable AnnotationValue<CircuitBreaker> circuitBreaker) {
    }
}
