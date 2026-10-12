/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import java.util.Collection;
import java.util.Set;

/**
 * Receives callbacks as the bean context creates beans and resolves their injection points, for example to log the
 * resolution or to record it as spans of a trace.
 *
 * <p>A tracer is registered with {@link ApplicationContextBuilder#beanResolutionTracer(BeanResolutionTracer)} or
 * discovered with {@link java.util.ServiceLoader} from {@code META-INF/services/io.micronaut.context.BeanResolutionTracer}.
 * Tracers exist before any bean does and therefore cannot be beans themselves. A discovered tracer is only active
 * when {@link #isEnabled(Activation)} returns {@code true}, which by default it does only if discovery was enabled
 * with {@link ApplicationContextBuilder#beanResolutionTracerDiscovery(boolean)}, the system property
 * {@code micronaut.inject.trace.tracers} or the environment variable
 * {@code MICRONAUT_INJECT_TRACE_TRACERS}. All active tracers, together with the tracer of the
 * {@link BeanResolutionTraceMode}, receive every callback, in the order they were registered with the tracer of
 * the mode first and the discovered tracers last. The class patterns of the {@link BeanResolutionTraceConfiguration}
 * only restrict the tracer of the mode.</p>
 *
 * <h2>Threading and ordering</h2>
 *
 * <ul>
 *     <li>A callback is invoked on the thread that resolves the bean, synchronously. The callbacks of the creation of
 *     a bean and of the injection points it resolves, including the beans created for them, are all invoked on that
 *     thread. Separate beans may be created concurrently on separate threads, so a tracer must be thread safe.</li>
 *     <li>Creations and injections nest strictly, like the frames of a call stack: every
 *     {@link #traceBeanCreation(BeanResolutionContext, BeanDefinition, Argument)} is followed, on the same thread,
 *     by exactly one {@link #traceBeanCreated(BeanResolutionContext, BeanDefinition)} or
 *     {@link #traceBeanCreationFailed(BeanResolutionContext, BeanDefinition, Throwable)}, and every
 *     {@link #traceInjectBean(BeanResolutionContext, BeanResolutionContext.Segment)} by exactly one
 *     {@link #traceInjectComplete(BeanResolutionContext, BeanResolutionContext.Segment)}, whether the injection
 *     succeeded or not. Any callback in between belongs to the innermost creation or injection still open. A tracer
 *     can therefore keep its open spans on a per thread stack.</li>
 *     <li>A bean created to satisfy an injection point is reported between the
 *     {@link #traceInjectBean(BeanResolutionContext, BeanResolutionContext.Segment)} and the
 *     {@link #traceInjectComplete(BeanResolutionContext, BeanResolutionContext.Segment)} of that injection point.
 *     A bean that its creation looks up from the context programmatically is reported nested in it too, although
 *     it is resolved with another {@link BeanResolutionContext}.</li>
 *     <li>When the creation of a bean fails, {@link #traceBeanCreationFailed(BeanResolutionContext, BeanDefinition, Throwable)}
 *     is invoked for it instead of {@link #traceBeanCreated(BeanResolutionContext, BeanDefinition)}, after the open
 *     injection points of the bean were completed, and then again for each enclosing creation that the failure
 *     fails.</li>
 * </ul>
 *
 * <p>Callbacks run on the path that resolves every bean, so they should be fast. A tracer must not throw: an
 * exception thrown by a callback propagates to, and fails, the resolution being traced. A callback must not create
 * beans either.</p>
 *
 * <p>The {@link BeanResolutionContext} passed to a callback is only valid during the callback. Besides the bean
 * definitions passed, a tracer may read its {@link BeanResolutionContext#getPath() path}, the injection points
 * being resolved with the innermost last, its {@link BeanResolutionContext#getBeansInCreation() beans in creation},
 * and keep state for the creation in its attributes.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface BeanResolutionTracer {

    /**
     * Decides whether a tracer discovered with {@link java.util.ServiceLoader} is active for a context. It is
     * invoked once each time the context starts, before any bean is created. Tracers registered with the
     * {@link ApplicationContextBuilder} are always active.
     *
     * <p>A tracer meant for development only can activate itself only for the matching environments, for example
     * {@code activation.discoveryEnabled() || activation.environmentNames().contains(Environment.DEVELOPMENT)}.</p>
     *
     * @param activation The activation
     * @return Whether the tracer is active, by default whether discovery was enabled
     * @since 5.3.0
     */
    default boolean isEnabled(Activation activation) {
        return activation.discoveryEnabled();
    }

    /**
     * Tracing the starting configuration of the context.
     * @param environment The environment.
     * @param beanReferences The loaded bean references
     * @param disabledBeans The disabled beans
     */
    default void traceInitialConfiguration(
        Environment environment,
        Collection<BeanDefinitionReference<Object>> beanReferences,
        Collection<DisabledBean<?>> disabledBeans) {
        // no-op
    }

    /**
     * Start tracing the creation of a bean.
     *
     * <p>Invoked for every bean created, including the beans created while another bean is created. The
     * {@link BeanResolutionContext#getPath() path} of the context is empty for a bean created on its own and
     * otherwise ends with the injection point the bean is created for. The
     * {@link BeanResolutionContext#getBeansInCreation() beans in creation} do not include the bean yet.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanDefinition The bean definition
     * @param beanType The bean type
     */
    void traceBeanCreation(
        BeanResolutionContext resolutionContext,
        BeanDefinition<?> beanDefinition,
        Argument<?> beanType);

    /**
     * Trace when a bean was resolved for a type.
     * @param resolutionContext The resolution context
     * @param beanType The bean type
     * @param qualifier The qualifier (can be {@code null})
     * @param bean The bean (can be {@code null} if a factory resolved to {@code null}
     * @param <T> The bean generic type
     */
    default <T> void traceBeanResolved(
        BeanResolutionContext resolutionContext,
        Argument<T> beanType,
        @Nullable Qualifier<T> qualifier,
        @Nullable T bean
    ) {
        // no-op
    }

    /**
     * Callback to trace when bean resolution detected disabled beans that
     * match the type and qualifier.
     * @param resolutionContext The resolution context
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param disabledBeanMessage The message
     * @param <T> The bean generic type
     */
    default <T> void traceBeanDisabled(
        BeanResolutionContext resolutionContext,
        Argument<T> beanType,
        @Nullable
        Qualifier<T> qualifier,
        String disabledBeanMessage) {
        // no-op
    }

    /**
     * Trace when a value is resolved.
     *
     * @see Environment#getProperty(String, Argument)
     *
     * @param resolutionContext The resolution context
     * @param argument The argument type.
     * @param property The property
     * @param value The value
     * @param <T> The value generic type
     */
    default <T> void traceValueResolved(
        BeanResolutionContext resolutionContext,
        Argument<T> argument,
        String property,
        @Nullable
        T value
    ) {
        // no-op
    }

    /**
     * Trace when a bean has been created and all bean creation listeners fired. Pairs with the preceding
     * {@link #traceBeanCreation(BeanResolutionContext, BeanDefinition, Argument)} on the same thread.
     *
     * @param resolutionContext The resolution context
     * @param beanDefinition The bean definition
     * @param <T> The bean type
     */
    <T> void traceBeanCreated(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> beanDefinition);

    /**
     * Trace when the creation of a bean failed. Pairs with the preceding
     * {@link #traceBeanCreation(BeanResolutionContext, BeanDefinition, Argument)} on the same thread, in place of
     * {@link #traceBeanCreated(BeanResolutionContext, BeanDefinition)}.
     *
     * @param resolutionContext The resolution context
     * @param beanDefinition The bean definition
     * @param failure The failure, which is rethrown after the callback
     * @param <T> The bean type
     * @since 5.3.0
     */
    default <T> void traceBeanCreationFailed(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> beanDefinition,
        Throwable failure) {
        // no-op
    }

    /**
     * Trace when a bean is about to be injected.
     *
     * <p>Called prior to injection occurring and any potential bean creation that it may trigger. The segment is
     * the last one of the {@link BeanResolutionContext#getPath() path}.</p>
     *
     * @param resolutionContext The resolution context
     * @param segment The segment that represents the injection point
     * @param <B> The bean type
     * @param <T> The injection point type
     */
    <B, T> void traceInjectBean(
        BeanResolutionContext resolutionContext,
        BeanResolutionContext.Segment<B, T> segment);

    /**
     * Called on the completion of resolution of an injection point, whether it succeeded or failed. Pairs with the
     * preceding {@link #traceInjectBean(BeanResolutionContext, BeanResolutionContext.Segment)} of the segment on the
     * same thread.
     *
     * @param resolutionContext The resolution context
     * @param segment The segment
     * @param <B> The bean type
     * @param <T> The injection point type
     */
    default <B, T> void traceInjectComplete(
        BeanResolutionContext resolutionContext,
        BeanResolutionContext.Segment<B, T> segment) {
        // no-op
    }

    /**
     * Trace the shutdown of the context.
     * @param beanContext The bean context
     */
    default void traceContextShutdown(BeanContext beanContext) {
        // no-op
    }

    /**
     * What a discovered tracer can base its activation on.
     *
     * @param environmentNames The active environment names of the context, empty for a context without an environment
     * @param discoveryEnabled Whether discovered tracers were enabled explicitly
     * @since 5.3.0
     */
    @Experimental
    record Activation(Set<String> environmentNames, boolean discoveryEnabled) {
    }
}
