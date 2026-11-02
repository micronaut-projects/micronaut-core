/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;

import java.util.Set;

/**
 * Creates a {@link BeanResolutionTracer} for a context, discovered with {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.micronaut.context.BeanResolutionTracerProvider}.
 *
 * <p>Every provider on the classpath is instantiated each time a context starts, before any bean is created, to be
 * asked whether its tracer is active, so its constructor must be cheap and free of side effects. The tracer itself is
 * only created when it is active. By default it is active only if discovery was enabled with
 * {@link ApplicationContextBuilder#beanResolutionTracerDiscovery(boolean)}, the system property
 * {@code micronaut.inject.trace.tracers} or the environment variable {@code MICRONAUT_INJECT_TRACE_TRACERS}, so that a
 * library on the classpath does not trace an application by accident.</p>
 *
 * <p>The tracers of the providers receive their callbacks in the {@link #getOrder() order} of the providers, the end
 * of a creation or an injection in the reverse order. The order of providers with the same order is unspecified.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface BeanResolutionTracerProvider extends Ordered {

    /**
     * Decides whether the tracer is active for a context. Invoked each time the context starts.
     *
     * <p>A tracer meant for development only can activate itself for the matching environments, for example with
     * {@code activation.isDiscoveryEnabled() || activation.getEnvironmentNames().contains(Environment.DEVELOPMENT)}.</p>
     *
     * @param activation What the activation can be based on
     * @return Whether the tracer is active, by default whether discovery was enabled
     */
    default boolean isEnabled(Activation activation) {
        return activation.isDiscoveryEnabled();
    }

    /**
     * Creates the tracer for a context it is {@link #isEnabled(Activation) active} for.
     *
     * @param activation What the activation was based on
     * @return The tracer
     */
    BeanResolutionTracer createTracer(Activation activation);

    /**
     * What the activation of a tracer can be based on.
     */
    @Experimental
    interface Activation {

        /**
         * @return The active environment names of the context, empty for a context without an environment
         */
        Set<String> getEnvironmentNames();

        /**
         * @return Whether discovered tracers were enabled explicitly
         */
        boolean isDiscoveryEnabled();
    }
}
