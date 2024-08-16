/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;

/**
 * An application context configurer is responsible
 * for configuring an application context before the
 * application/function is started.
 *
 * <p>Application context configurers must be registered
 * as services. Those services are automatically called
 * whenever a new application context builder is created.</p>
 *
 * <p>An application context annotated with
 * {@link io.micronaut.context.annotation.ContextConfigurer}
 * will automatically be registered as a service provider.</p>
 *
 * @since 3.2
 */
@Experimental
public interface ApplicationContextConfigurer extends Ordered {

    /**
     * A default configurer which does nothing.
     */
    ApplicationContextConfigurer NO_OP = new ApplicationContextConfigurer() {
    };

    /**
     * Configures the application context builder.
     *
     * <p>This method allows the programmatic enhancement of the context prior to construction.</p>
     *
     * <p>The {@link ApplicationContextBuilder} interface features methods to add environments, property sources etc.</p>
     *
     * @param builder the builder to configure
     * @see ApplicationContextBuilder
     */
    default void configure(@NonNull ApplicationContextBuilder builder) {
        // no-op
    }

    /**
     * Configures the application context.
     *
     * <p>This method will be called after the context is fully configured but
     * prior to starting the context.</p>
     *
     * @param applicationContext The context.
     * @since 4.5.0
     */
    default void configure(@NonNull ApplicationContext applicationContext) {
        // no-op
    }
}
