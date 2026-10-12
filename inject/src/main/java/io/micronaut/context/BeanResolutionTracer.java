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
import java.util.List;
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
 *     {@link #traceBeanCreation(Resolution, BeanDefinition, Argument)} is followed, on the same thread,
 *     by exactly one {@link #traceBeanCreated(Resolution, BeanDefinition)} or
 *     {@link #traceBeanCreationFailed(Resolution, BeanDefinition, Throwable)}, and every
 *     {@link #traceInjectBean(Resolution, Injection)} by exactly one {@link #traceInjectComplete(Resolution, Injection)}
 *     with the same {@link Injection}, whether the injection succeeded or not. Any callback in between belongs to the
 *     innermost creation or injection still open. A tracer can therefore keep its open spans on a per thread
 *     stack.</li>
 *     <li>A bean created to satisfy an injection point is reported between the
 *     {@link #traceInjectBean(Resolution, Injection)} and the {@link #traceInjectComplete(Resolution, Injection)} of
 *     that injection point. A bean that its creation looks up from the context programmatically is reported nested
 *     in it too, although it is part of another {@link Resolution}.</li>
 *     <li>When the creation of a bean fails, {@link #traceBeanCreationFailed(Resolution, BeanDefinition, Throwable)}
 *     is invoked for it instead of {@link #traceBeanCreated(Resolution, BeanDefinition)}, after the open
 *     injection points of the bean were completed, and then again for each enclosing creation that the failure
 *     fails.</li>
 * </ul>
 *
 * <p>Callbacks run on the path that resolves every bean, so they should be fast. A tracer must not throw: an
 * exception thrown by a callback propagates to, and fails, the resolution being traced. A callback must not create
 * beans either.</p>
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
     * {@link Resolution#getInjectionPath() injection path} is empty for a bean created on its own and otherwise ends
     * with the injection point the bean is created for. The {@link Resolution#getBeansInCreation() beans in creation}
     * do not include the bean yet.</p>
     *
     * @param resolution The resolution in progress
     * @param beanDefinition The bean definition
     * @param beanType The bean type
     */
    void traceBeanCreation(
        Resolution resolution,
        BeanDefinition<?> beanDefinition,
        Argument<?> beanType);

    /**
     * Trace when a bean was resolved for a type.
     * @param resolution The resolution in progress
     * @param beanType The bean type
     * @param qualifier The qualifier (can be {@code null})
     * @param bean The bean (can be {@code null} if a factory resolved to {@code null}
     * @param <T> The bean generic type
     */
    default <T> void traceBeanResolved(
        Resolution resolution,
        Argument<T> beanType,
        @Nullable Qualifier<T> qualifier,
        @Nullable T bean
    ) {
        // no-op
    }

    /**
     * Callback to trace when bean resolution detected disabled beans that
     * match the type and qualifier.
     * @param resolution The resolution in progress
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param disabledBeanMessage The message
     * @param <T> The bean generic type
     */
    default <T> void traceBeanDisabled(
        Resolution resolution,
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
     * @param resolution The resolution in progress
     * @param argument The argument type.
     * @param property The property
     * @param value The value
     * @param <T> The value generic type
     */
    default <T> void traceValueResolved(
        Resolution resolution,
        Argument<T> argument,
        String property,
        @Nullable
        T value
    ) {
        // no-op
    }

    /**
     * Trace when a bean has been created and all bean creation listeners fired. Pairs with the preceding
     * {@link #traceBeanCreation(Resolution, BeanDefinition, Argument)} on the same thread.
     *
     * @param resolution The resolution in progress
     * @param beanDefinition The bean definition
     * @param <T> The bean type
     */
    <T> void traceBeanCreated(
        Resolution resolution,
        BeanDefinition<T> beanDefinition);

    /**
     * Trace when the creation of a bean failed. Pairs with the preceding
     * {@link #traceBeanCreation(Resolution, BeanDefinition, Argument)} on the same thread, in place of
     * {@link #traceBeanCreated(Resolution, BeanDefinition)}.
     *
     * @param resolution The resolution in progress
     * @param beanDefinition The bean definition
     * @param failure The failure, which is rethrown after the callback
     * @param <T> The bean type
     */
    default <T> void traceBeanCreationFailed(
        Resolution resolution,
        BeanDefinition<T> beanDefinition,
        Throwable failure) {
        // no-op
    }

    /**
     * Trace when an injection point is about to be resolved.
     *
     * <p>Called prior to injection occurring and any potential bean creation that it may trigger. The injection is
     * the last one of the {@link Resolution#getInjectionPath() injection path}.</p>
     *
     * @param resolution The resolution in progress
     * @param injection The injection point
     */
    void traceInjectBean(
        Resolution resolution,
        Injection injection);

    /**
     * Called on the completion of resolution of an injection point, whether it succeeded or failed. Pairs with the
     * preceding {@link #traceInjectBean(Resolution, Injection)} of the same injection on the same thread.
     *
     * @param resolution The resolution in progress
     * @param injection The injection point
     */
    default void traceInjectComplete(
        Resolution resolution,
        Injection injection) {
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
     */
    @Experimental
    record Activation(Set<String> environmentNames, boolean discoveryEnabled) {
    }

    /**
     * A read-only view of a bean resolution in progress, passed to the callbacks of a tracer. It reflects the
     * resolution at the time of the callback and must only be used during the callback.
     */
    @Experimental
    interface Resolution {

        /**
         * @return The bean context that resolves the bean
         */
        BeanContext getBeanContext();

        /**
         * The definitions of the beans being created in this resolution, from the outermost to the innermost, the
         * innermost being the bean whose dependencies are being resolved. A bean created for an injection point of
         * another bean is nested in it. Beans that a bean looks up programmatically while it is created are resolved
         * in another resolution and are not included.
         *
         * @return The beans in creation
         */
        List<BeanDefinition<?>> getBeansInCreation();

        /**
         * The injection points being resolved, from the outermost to the innermost.
         *
         * @return The injection path
         */
        List<Injection> getInjectionPath();
    }

    /**
     * An injection point being resolved: a constructor or method parameter, a field, a bean named by an annotation
     * or the event of an event listener. The same instance is passed to
     * {@link #traceInjectBean(Resolution, Injection)} and {@link #traceInjectComplete(Resolution, Injection)}.
     */
    @Experimental
    interface Injection {

        /**
         * @return The kind of injection point
         */
        Kind getKind();

        /**
         * @return The bean that declares the injection point
         */
        BeanDefinition<?> getDeclaringBean();

        /**
         * @return The name of the field or method, or of the constructor, that declares the injection point
         */
        String getMemberName();

        /**
         * @return The argument resolved: the parameter, the field, the annotation member or the event type
         */
        Argument<?> getArgument();

        /**
         * The kinds of injection points.
         */
        enum Kind {
            /**
             * A parameter of a constructor, or of the factory method of a bean.
             */
            CONSTRUCTOR,
            /**
             * A parameter of an injected method.
             */
            METHOD,
            /**
             * An injected field.
             */
            FIELD,
            /**
             * A bean named by an annotation member.
             */
            ANNOTATION,
            /**
             * The event an event listener receives.
             */
            EVENT_LISTENER
        }
    }
}
