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
package io.micronaut.web.router;

import io.micronaut.http.uri.RouteTemplate;

/**
 * The colon language of the tests, whose routes ignore matrix parameters like JAX-RS: the part of
 * each segment from a {@code ;} is removed from the path the routes match.
 */
public class MatrixColonRouteTemplateEngine extends ColonRouteTemplateEngine {

    public static final String ID = "test.colon.matrix";

    public MatrixColonRouteTemplateEngine() {
        super(ID);
    }

    public static RouteTemplate template(String expression) {
        return RouteTemplate.of(ID, expression);
    }

    @Override
    public String matchingPath(String rawPath) {
        return stripMatrixParameters(rawPath);
    }

    static String stripMatrixParameters(String path) {
        if (path.indexOf(';') < 0) {
            return path;
        }
        StringBuilder result = new StringBuilder(path.length());
        int start = 0;
        while (true) {
            int slash = path.indexOf('/', start);
            int end = slash < 0 ? path.length() : slash;
            int semicolon = path.indexOf(';', start);
            result.append(path, start, semicolon >= 0 && semicolon < end ? semicolon : end);
            if (slash < 0) {
                return result.toString();
            }
            result.append('/');
            start = slash + 1;
        }
    }
}
