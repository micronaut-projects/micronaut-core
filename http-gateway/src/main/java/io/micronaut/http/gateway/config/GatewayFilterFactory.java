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
package io.micronaut.http.gateway.config;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.gateway.GatewayFilter;

import java.util.List;
import java.util.function.Function;

/**
 * Creates a filter of a gateway route of the configuration, e.g. {@code StripPrefix=2}: a bean
 * named after the filter, e.g. {@code @Named("StripPrefix")}, looked up by the name the
 * configuration gives. An application adds a filter with a bean of its own name.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface GatewayFilterFactory {

    /**
     * @return The names of the arguments of the shortcut syntax, in order, e.g.
     * {@code [name, value]} for {@code AddRequestHeader=X-Gateway, mn}; the last one takes the
     * rest of the arguments. A single name starting with {@code *} takes every argument as a
     * list, e.g. {@code [*names]}
     */
    List<String> shortcutFields();

    /**
     * @param arguments The arguments
     * @return The filter
     */
    GatewayFilter create(GatewayArguments arguments);

    /**
     * @param shortcutFields See {@link #shortcutFields()}
     * @param create         Creates the filter
     * @return A factory
     */
    static GatewayFilterFactory of(List<String> shortcutFields, Function<GatewayArguments, GatewayFilter> create) {
        return new GatewayFilterFactory() {
            @Override
            public List<String> shortcutFields() {
                return shortcutFields;
            }

            @Override
            public GatewayFilter create(GatewayArguments arguments) {
                return create.apply(arguments);
            }
        };
    }
}
