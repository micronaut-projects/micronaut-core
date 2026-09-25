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
package io.micronaut.web.router.processor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.uri.ParsedRouteTemplate;

/**
 * The optional build-time lowering of the templates of a
 * {@link io.micronaut.http.uri.spi.RouteTemplateEngine route template engine}: it turns a
 * template the engine parsed into the exact matching operations of a generated parser, or says
 * why it cannot. A template that is not lowered is matched at runtime by the engine, with its
 * {@link io.micronaut.http.uri.RoutePattern}; that is a supported fallback, not an error.
 *
 * <p>Compilers are registered with a
 * {@code META-INF/services/io.micronaut.web.router.processor.RouteTemplateCompiler} file on the
 * annotation processor path, next to the engine. A lowering must accept exactly the paths the
 * pattern of the engine accepts, and capture the same raw values: the coarse
 * {@link ParsedRouteTemplate#pathSegments() path segments} of a template describe possible
 * overlaps, not matching, and are not enough to lower a template.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteTemplateCompiler {

    /**
     * @return The identifier of the engine whose templates this compiler lowers
     */
    String engineId();

    /**
     * @return The version of the engine whose semantics this compiler reproduces; a template
     * parsed by another version is not lowered
     */
    String engineVersion();

    /**
     * Lower a template.
     *
     * @param template The template, as the engine parsed it
     * @return The lowered template, or {@link LoweredTemplate#unsupported(String)} with the reason
     */
    LoweredTemplate lower(ParsedRouteTemplate template);
}
