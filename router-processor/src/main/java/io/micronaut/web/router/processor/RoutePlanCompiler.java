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
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The route compiler: plans the routes a processor {@link RouteDescription describes} and
 * generates the route plan, a class implementing {@link RoutePlan} built with Sourcegen, and its
 * versioned descriptor, {@code META-INF/micronaut/routes/v1/<plan>.json}.
 *
 * <p>Each template is parsed by its engine, resolved with {@link RouteTemplateEngines}, and
 * lowered by the {@link RouteTemplateCompiler} of the engine. A template without a compatible
 * lowering is recorded in the plan with the reason and matched at runtime by its engine; a
 * template whose engine is missing, or that the engine rejects, fails the compilation.</p>
 *
 * <p>Controllers are compiled by the visitors of this module; a processor of another web
 * framework compiles the routes of its own annotations with this class, and generates its
 * declarations as {@link io.micronaut.web.router.spi.PlannedRouteDeclaration}s of the plan.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RoutePlanCompiler {

    /**
     * The directory of the descriptors, versioned with their format.
     */
    public static final String DESCRIPTORS_DIRECTORY = "micronaut/routes/v1/";

    private final RouteTemplateEngines engines;
    private final Map<String, RouteTemplateCompiler> compilers;

    /**
     * A compiler with the engines and the template compilers of the annotation processor path.
     */
    public RoutePlanCompiler() {
        this(RouteTemplateEngines.defaults(), SoftServiceLoader.load(RouteTemplateCompiler.class, RoutePlanCompiler.class.getClassLoader()).collectAll());
    }

    /**
     * A compiler with explicit engines and template compilers.
     *
     * @param engines   The engines
     * @param compilers The template compilers
     * @throws IllegalArgumentException if two compilers lower the templates of the same engine
     */
    public RoutePlanCompiler(RouteTemplateEngines engines, Collection<? extends RouteTemplateCompiler> compilers) {
        this.engines = engines;
        Map<String, RouteTemplateCompiler> byEngine = new TreeMap<>();
        byEngine.put(RouteTemplate.MICRONAUT, new MicronautRouteTemplateCompiler());
        for (RouteTemplateCompiler compiler : compilers) {
            RouteTemplateCompiler existing = byEngine.put(compiler.engineId(), compiler);
            if (existing != null && existing.getClass() != compiler.getClass()) {
                throw new IllegalArgumentException("Two route template compilers for the engine '" + compiler.engineId() + "': " + existing + " and " + compiler);
            }
        }
        this.compilers = byEngine;
    }

    /**
     * Plan routes: parse and lower their templates, and describe their slots.
     *
     * @param className The name of the class to generate
     * @param id        The identity of the plan, unique in the application, e.g. {@code controller:example.PetController}
     * @param owners    The controller types whose routes are described, empty for a plan of declarations
     * @param routes    The routes, in slot order
     * @return The plan
     * @throws IllegalArgumentException if two routes have the same key, or a template cannot be parsed
     */
    public CompiledRoutePlan plan(String className, String id, List<String> owners, List<RouteDescription> routes) {
        List<RouteSlot> slots = new ArrayList<>(routes.size());
        List<LoweredTemplate> lowered = new ArrayList<>(routes.size());
        Set<String> keys = new HashSet<>();
        for (RouteDescription route : routes) {
            if (!keys.add(route.key())) {
                throw new IllegalArgumentException("Two routes have the key " + route.key() + ": the keys of the declarations of a plan must be unique");
            }
            ParsedRouteTemplate parsed = engines.parse(route.template());
            LoweredTemplate lowering = lower(parsed);
            slots.add(new RouteSlot(
                route.key(),
                route.httpMethodName(),
                route.template(),
                parsed.engineVersion(),
                parsed.requiredPrefix(),
                parsed.rawLength(),
                parsed.pathVariableCount(),
                lowering.captures().toArray(String[]::new),
                lowering.isSupported(),
                lowering.unsupportedReason(),
                route.controller()
            ));
            lowered.add(lowering);
        }
        return new CompiledRoutePlan(className, id, List.copyOf(owners), List.copyOf(slots), List.copyOf(lowered));
    }

    private LoweredTemplate lower(ParsedRouteTemplate parsed) {
        RouteTemplateCompiler compiler = compilers.get(parsed.engineId());
        if (compiler == null) {
            return LoweredTemplate.unsupported("no route template compiler for the engine '" + parsed.engineId() + "'");
        }
        if (!compiler.engineVersion().equals(parsed.engineVersion())) {
            return LoweredTemplate.unsupported("the route template compiler of the engine '" + parsed.engineId() + "' is for the version "
                + compiler.engineVersion() + ", not " + parsed.engineVersion());
        }
        return compiler.lower(parsed);
    }

    /**
     * Plan routes and write the generated plan and its descriptor.
     *
     * @param className           The name of the class to generate
     * @param id                  The identity of the plan
     * @param owners              The controller types whose routes are described
     * @param routes              The routes, in slot order
     * @param registerService     Whether to register the plan as a service of {@link RoutePlan}, as the plans of controllers are
     * @param context             The visitor context
     * @param originatingElements The elements the routes originate from
     * @return The plan
     * @throws IOException if the files cannot be written
     */
    public CompiledRoutePlan compile(String className,
                                     String id,
                                     List<String> owners,
                                     List<RouteDescription> routes,
                                     boolean registerService,
                                     VisitorContext context,
                                     Element... originatingElements) throws IOException {
        CompiledRoutePlan plan = plan(className, id, owners, routes);
        try (OutputStream outputStream = context.visitClass(className, originatingElements)) {
            outputStream.write(ByteCodeWriterUtils.writeByteCode(RoutePlanWriter.write(plan), context));
        }
        GeneratedFile descriptor = context.visitMetaInfFile(descriptorPath(id), originatingElements).orElse(null);
        if (descriptor != null) {
            try (Writer writer = descriptor.openWriter()) {
                writer.write(RouteDescriptors.write(plan));
            }
        }
        if (registerService && originatingElements.length > 0) {
            context.visitServiceDescriptor(RoutePlan.class.getName(), className, originatingElements[0]);
        }
        return plan;
    }

    /**
     * @param id The identity of a plan
     * @return The path of its descriptor under {@code META-INF}
     */
    public static String descriptorPath(String id) {
        StringBuilder name = new StringBuilder(DESCRIPTORS_DIRECTORY);
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            name.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '$' ? c : '_');
        }
        return name.append(".json").toString();
    }
}
