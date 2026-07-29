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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import org.graalvm.polyglot.Context;

/**
 * Customizes every GraalPy context builder created by Micronaut.
 *
 * <p>Implementations are loaded with {@link java.util.ServiceLoader} semantics and invoked in
 * {@link #getOrder() order} each time a context is built. Implementations may therefore be
 * invoked multiple times and should be idempotent. A customizer must not build or retain the
 * supplied builder.</p>
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
@FunctionalInterface
public interface GraalPyContextCustomizer extends Ordered {

    /**
     * Customize a context builder before its context is created.
     *
     * @param builder The builder to customize
     */
    void customize(Context.Builder builder);

    @Override
    default int getOrder() {
        return 0;
    }
}
