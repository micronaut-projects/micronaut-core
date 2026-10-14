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
package io.micronaut.python.annotation.processing.test.javabases;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java base with a method named {@code $}, the shape of browserless-test's {@code $(Class)},
 * which no Python name can call, and same-arity overloads taking a {@code Class} or a
 * {@code String}, the shape of {@code navigate(String, Class)}.
 */
public class RoutingBase {

    private final List<String> routes = new ArrayList<>();

    public RoutingBase $(String selector) {
        routes.add("$" + selector);
        return this;
    }

    public void open(String route, Class<?> target) {
        routes.add(route + "->" + target.getSimpleName());
    }

    public void open(String route, String fallback) {
        routes.add(route + "~" + fallback);
    }

    public List<String> getRoutes() {
        return routes;
    }
}
