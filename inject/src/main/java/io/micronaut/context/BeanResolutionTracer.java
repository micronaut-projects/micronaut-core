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

/**
 * Receives callbacks as the bean context creates beans and resolves their injection points, for example to log the
 * resolution or to record it as spans of a trace.
 *
 * <p>A tracer is registered with {@link ApplicationContextBuilder#beanResolutionTracer(BeanResolutionTracer)}, or
 * created by a {@link BeanResolutionTracerProvider} discovered with {@link java.util.ServiceLoader}. Tracers exist
 * before any bean does and therefore cannot be beans themselves. All active tracers, together with the tracer of the
 * {@link BeanResolutionTraceMode}, receive every callback: the tracer of the mode first, then the registered tracers
 * in the order they were registered, then the discovered tracers in the order of their providers. The class
 * patterns of the {@link BeanResolutionTraceConfiguration} only restrict the tracer of the mode.</p>
 *
 * <h2>What is traced</h2>
 *
 * <p>The creation of every bean is traced, whether it is created to be resolved, for an injection point, or with
 * one of the {@code createBean} methods of the {@link BeanContext}. The creation of a bean encloses the resolution
 * of its injection points: the parameters of its constructor or factory method, its injected fields and methods,
 * the beans named by its annotations and the events of its event listeners.</p>
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
 * exception thrown by a callback propagates to, and fails, the resolution being traced, except one thrown by
 * {@link #traceBeanCreationFailed(Resolution, BeanDefinition, Throwable)}, which is added as suppressed to the
 * failure of the creation. Even then the pairing holds: the other tracers still receive the callback, and a
 * creation or an injection whose start callback throws is ended at once with
 * {@link #traceBeanCreationFailed(Resolution, BeanDefinition, Throwable)} or
 * {@link #traceInjectComplete(Resolution, Injection)}. A callback must not create beans either.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface BeanResolutionTracer {

    /**
     * Tracing the starting configuration of the context. Only invoked by an {@link ApplicationContext}, which has an
     * environment, when it is configured.
     *
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
     * <p>The {@link Resolution#getInjectionPath() injection path} is empty for a bean created on its own, and
     * otherwise ends with the innermost injection point being resolved. That is the injection point the bean is
     * created for, except for the factory of a bean produced by a factory: the lookup of the factory is not an
     * injection point, so the path of the factory ends with the injection point the produced bean is created for, if
     * any.
     * The {@link Resolution#getBeansInCreation() beans in creation} do not include the bean yet.</p>
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
     */
    void traceBeanCreated(
        Resolution resolution,
        BeanDefinition<?> beanDefinition);

    /**
     * Trace when the creation of a bean failed. Pairs with the preceding
     * {@link #traceBeanCreation(Resolution, BeanDefinition, Argument)} on the same thread, in place of
     * {@link #traceBeanCreated(Resolution, BeanDefinition)}.
     *
     * @param resolution The resolution in progress
     * @param beanDefinition The bean definition
     * @param failure The failure, which is rethrown after the callback
     */
    default void traceBeanCreationFailed(
        Resolution resolution,
        BeanDefinition<?> beanDefinition,
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
     * A read-only view of a bean resolution in progress, passed to the callbacks of a tracer. It reflects the
     * resolution at the time of the callback and must only be used during the callback.
     *
     * <p>The identity of a resolution is not stable: the callbacks of a single creation may receive different
     * instances, as the context copies a resolution in progress, for example to create a scoped bean. A tracer links
     * a creation to the creation it is nested in with {@link #getBeansInCreation()} or its own per thread stack, not
     * with the identity of the resolution.</p>
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
     * An injection point being resolved: a parameter of a constructor, a factory method or an injected method, a
     * field, a bean named by an annotation or the event of an event listener. The same instance is passed to
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
         * @return The name of the field or method that declares the injection point, {@code <init>} for a constructor
         */
        String getMemberName();

        /**
         * @return The argument resolved: the parameter, the field, the annotation member or the event type
         */
        Argument<?> getArgument();

        /**
         * The qualifier the injection point declares, such as {@code @Named}, resolved once, when it is first asked
         * for.
         *
         * @return The qualifier, or {@code null} for none
         */
        @Nullable Qualifier<?> getQualifier();

        /**
         * The kinds of injection points.
         */
        enum Kind {
            /**
             * A parameter of a constructor, or of a static {@link io.micronaut.core.annotation.Creator} method of the bean.
             */
            CONSTRUCTOR,
            /**
             * A parameter of the {@link io.micronaut.context.annotation.Factory} method that produces a bean.
             */
            FACTORY_METHOD,
            /**
             * A parameter of an injected method, a setter included.
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
