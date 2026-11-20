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
import io.micronaut.http.gateway.GatewayRoute;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Applies a predicate of a gateway route of the configuration, e.g.
 * {@code Header=X-Tenant, \w+}, to the route: a path pattern or a
 * {@link io.micronaut.web.router.builder.RequestCondition}. A bean named after the predicate,
 * e.g. {@code @Named("Header")}, looked up by the name the configuration gives. An application
 * adds a predicate with a bean of its own name.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface GatewayPredicateFactory {

    /**
     * @return The names of the arguments of the shortcut syntax, see
     * {@link GatewayFilterFactory#shortcutFields()}
     */
    List<String> shortcutFields();

    /**
     * @param route     The route to apply the predicate to
     * @param arguments The arguments
     */
    void apply(GatewayRoute.Builder route, GatewayArguments arguments);

    /**
     * @param shortcutFields See {@link #shortcutFields()}
     * @param apply          Applies the predicate
     * @return A factory
     */
    static GatewayPredicateFactory of(List<String> shortcutFields, BiConsumer<GatewayRoute.Builder, GatewayArguments> apply) {
        return new GatewayPredicateFactory() {
            @Override
            public List<String> shortcutFields() {
                return shortcutFields;
            }

            @Override
            public void apply(GatewayRoute.Builder route, GatewayArguments arguments) {
                apply.accept(route, arguments);
            }
        };
    }
}
