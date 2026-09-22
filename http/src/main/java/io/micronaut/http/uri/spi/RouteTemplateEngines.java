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
package io.micronaut.http.uri.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RoutePattern;
import io.micronaut.http.uri.RouteTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The registered {@link RouteTemplateEngine}s, by identifier.
 *
 * <p>The {@link #defaults() default} registry has the engine of the
 * {@link RouteTemplate#MICRONAUT Micronaut} language and the engines of the Java service loader.
 * Service loading, rather than beans, lets annotation processors and code without an application
 * context resolve the same engines; the result does not depend on the order in which the services
 * are found, since two engines with the same identifier are an error. A template of an engine that
 * is not registered is an error too: it is never parsed as a Micronaut template instead.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RouteTemplateEngines {

    private final Map<String, RouteTemplateEngine> engines;

    private RouteTemplateEngines(Map<String, RouteTemplateEngine> engines) {
        this.engines = engines;
    }

    /**
     * The engine of the Micronaut language and the engines of the Java service loader, loaded
     * once.
     *
     * @return The engines
     * @throws IllegalStateException if two engines have the same identifier
     */
    public static RouteTemplateEngines defaults() {
        return Defaults.ENGINES;
    }

    /**
     * The engine of the Micronaut language and the given engines.
     *
     * @param engines The engines
     * @return The registry
     * @throws IllegalStateException if two engines have the same identifier
     */
    public static RouteTemplateEngines of(Collection<? extends RouteTemplateEngine> engines) {
        Map<String, RouteTemplateEngine> byId = new TreeMap<>();
        byId.put(RouteTemplate.MICRONAUT, MicronautRouteTemplateEngine.INSTANCE);
        for (RouteTemplateEngine engine : engines) {
            Objects.requireNonNull(engine, "engine");
            String id = Objects.requireNonNull(engine.id(), "The identifier of a route template engine");
            RouteTemplateEngine existing = byId.putIfAbsent(id, engine);
            if (existing != null && existing != engine) {
                throw new IllegalStateException("Duplicate route template engines with the identifier '" + id + "': "
                    + existing + " and " + engine);
            }
        }
        return new RouteTemplateEngines(Collections.unmodifiableMap(byId));
    }

    /**
     * @return The identifiers of the registered engines, sorted
     */
    public Set<String> ids() {
        return engines.keySet();
    }

    /**
     * @param id The identifier of an engine
     * @return Whether the engine is registered
     */
    public boolean contains(String id) {
        return engines.containsKey(id);
    }

    /**
     * @param id The identifier of an engine
     * @return The engine
     * @throws IllegalArgumentException if no engine has the identifier
     */
    public RouteTemplateEngine engine(String id) {
        RouteTemplateEngine engine = engines.get(id);
        if (engine == null) {
            throw new IllegalArgumentException("No route template engine with the identifier '" + id
                + "' is registered. Registered engines: " + engines.keySet()
                + ". Engines are registered with a META-INF/services/io.micronaut.http.uri.spi.RouteTemplateEngine file");
        }
        return engine;
    }

    /**
     * Parse a template with its engine, and check the parsed template describes the facts the
     * router needs.
     *
     * @param template The template
     * @return The parsed template
     * @throws IllegalArgumentException if the engine is not registered or the template is not valid
     * @throws IllegalStateException    if the engine does not describe the template correctly
     */
    public ParsedRouteTemplate parse(RouteTemplate template) {
        RouteTemplateEngine engine = engine(template.engineId());
        return checked(engine, engine.parse(template), template);
    }

    /**
     * Nest a template under another with their engine.
     *
     * @param parent The template of the enclosing route
     * @param child  The nested template
     * @return The composed template
     * @throws IllegalArgumentException if the templates are of different engines
     */
    public ParsedRouteTemplate nest(ParsedRouteTemplate parent, ParsedRouteTemplate child) {
        if (!parent.engineId().equals(child.engineId())) {
            throw new IllegalArgumentException("Cannot nest the route template '" + child.template()
                + "' of the engine '" + child.engineId() + "' in the route template '" + parent.template()
                + "' of the engine '" + parent.engineId() + "': nesting across route template engines is not supported");
        }
        RouteTemplateEngine engine = engine(parent.engineId());
        return checked(engine, engine.nest(parent, child), child.template());
    }

    /**
     * Mount a template under a literal path with its engine.
     *
     * @param prefix   The literal prefix
     * @param template The template
     * @return The mounted template
     */
    public ParsedRouteTemplate mount(String prefix, ParsedRouteTemplate template) {
        RouteTemplateEngine engine = engine(template.engineId());
        return checked(engine, engine.mount(prefix, template), template.template());
    }

    /**
     * @param template A parsed template
     * @return The matcher its engine prepared
     */
    public RoutePattern matcher(ParsedRouteTemplate template) {
        return Objects.requireNonNull(engine(template.engineId()).matcher(template), "The route pattern");
    }

    private static ParsedRouteTemplate checked(RouteTemplateEngine engine, ParsedRouteTemplate parsed, RouteTemplate source) {
        if (engine == MicronautRouteTemplateEngine.INSTANCE) {
            // the facts are computed lazily, and are correct by construction
            return parsed;
        }
        List<String> problems = new ArrayList<>(2);
        if (parsed == null) {
            problems.add("no parsed template");
        } else {
            if (!engine.id().equals(parsed.engineId())) {
                problems.add("the engine identifier is '" + parsed.engineId() + "'");
            }
            if (!Objects.equals(engine.version(), parsed.engineVersion())) {
                problems.add("the engine version is '" + parsed.engineVersion() + "', not '" + engine.version() + "'");
            }
            if (parsed.variables() == null) {
                problems.add("no variables");
            }
            String prefix = parsed.requiredPrefix();
            if (prefix == null) {
                problems.add("no required prefix");
            } else if (!prefix.isEmpty() && prefix.charAt(0) != '/') {
                problems.add("the required prefix '" + prefix + "' does not start with a slash");
            }
            if (parsed.rawLength() < 0) {
                problems.add("a negative raw length");
            }
            if (parsed.pathVariableCount() < 0) {
                problems.add("a negative path variable count");
            }
            if (parsed.patternVariableCount() < 0) {
                problems.add("a negative pattern variable count");
            } else if (parsed.pathVariableCount() >= 0 && parsed.patternVariableCount() > parsed.pathVariableCount()) {
                problems.add("a pattern variable count greater than the path variable count");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The route template engine '" + engine.id() + "' described the template '"
                + source.expression() + "' incorrectly for the Micronaut route selection policy: " + String.join(", ", problems));
        }
        return parsed;
    }

    /**
     * Loads the default engines once.
     */
    private static final class Defaults {
        static final RouteTemplateEngines ENGINES = of(SoftServiceLoader.load(RouteTemplateEngine.class).collectAll());
    }
}
