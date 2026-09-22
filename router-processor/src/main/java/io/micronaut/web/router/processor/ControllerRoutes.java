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

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.UriMapping;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.web.router.RouteDefinitions;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import io.micronaut.web.router.spi.ControllerRoute;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes the routes of a controller at compile time with {@link RouteDefinitions}, the
 * interpretation of the controller annotations the runtime route builder uses too, with the
 * default naming strategy and no context path, which is when the router uses the plans of
 * controllers.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ControllerRoutes {

    /**
     * The namespace of the keys of controller routes.
     */
    static final String NAMESPACE = "controller";
    private static final String PLACEHOLDER = "${";
    private static final String CLASS_SUFFIX = "$RoutePlan";
    /**
     * The naming strategy the runtime uses with route plans.
     */
    private static final HyphenatedUriNamingStrategy NAMING_STRATEGY = new HyphenatedUriNamingStrategy();

    private ControllerRoutes() {
    }

    /**
     * @param element A class
     * @return Whether the class is a controller with routes
     */
    static boolean isController(ClassElement element) {
        return element.hasStereotype(Controller.class) && !element.isAbstract() && !element.isInterface();
    }

    /**
     * @param controller A controller
     * @return The name of the class of its plan, next to the controller
     */
    static String planClassName(ClassElement controller) {
        String name = controller.getName();
        String packageName = controller.getPackageName();
        String simpleName = packageName.isEmpty() ? name : name.substring(packageName.length() + 1);
        return (packageName.isEmpty() ? "" : packageName + '.') + '$' + simpleName + CLASS_SUFFIX;
    }

    /**
     * @param controller A controller
     * @return The identity of its plan
     */
    static String planId(ClassElement controller) {
        return NAMESPACE + ':' + controller.getName();
    }

    /**
     * Describe the routes of a controller the same way the runtime route builder derives them.
     *
     * @param controller The controller
     * @param context    The visitor context
     * @return The routes, or {@code null} if the controller cannot be compiled
     */
    static @Nullable List<RouteDescription> describe(ClassElement controller, VisitorContext context) {
        String controllerUri = controller.stringValue(UriMapping.class)
            .orElseGet(() -> controller.stringValue(Controller.class).orElse(UriMapping.DEFAULT_URI));
        if (hasPlaceholder(controllerUri) || controller.stringValue(Controller.class, "port").filter(ControllerRoutes::hasPlaceholder).isPresent()) {
            return skip(controller, context);
        }
        RouteDefinitions.UriResolver uriResolver = new RouteDefinitions.UriResolver() {
            @Override
            public String controllerUri() {
                // HyphenatedUriNamingStrategy#resolveUri(BeanDefinition) without a context path
                return String.valueOf(NAMING_STRATEGY.normalizeUri(controllerUri));
            }

            @Override
            public String methodUri(String methodName) {
                return NAMING_STRATEGY.resolveUri(methodName);
            }
        };
        List<RouteDescription> routes = new ArrayList<>();
        for (MethodElement method : controller.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
            if (!method.hasStereotype(Executable.class)
                || !method.hasStereotype(HttpMethodMapping.class) && !method.hasDeclaredAnnotation(UriMapping.class)) {
                // the runtime only routes the executable methods of a controller
                continue;
            }
            if (hasPlaceholder(method.stringValues(HttpMethodMapping.class, "uris"))
                || method.stringValue(HttpMethodMapping.class).filter(ControllerRoutes::hasPlaceholder).isPresent()
                || hasPlaceholder(method.stringValues(UriMapping.class, "uris"))
                || method.stringValue(UriMapping.class).filter(ControllerRoutes::hasPlaceholder).isPresent()
                || hasPlaceholder(method.stringValues(Consumes.class))
                || hasPlaceholder(method.stringValues(Produces.class))) {
                return skip(controller, context);
            }
            List<RouteDefinitions.RouteSpec> specs;
            try {
                specs = RouteDefinitions.resolve(controller, method, method.getName(), uriResolver);
            } catch (RuntimeException e) {
                return skip(controller, context);
            }
            String[] argumentTypes = Arrays.stream(method.getParameters())
                .map(RouteDescription::erasedName)
                .toArray(String[]::new);
            Map<String, Integer> aliases = new HashMap<>();
            for (RouteDefinitions.RouteSpec spec : specs) {
                if (hasPlaceholder(spec.uri())) {
                    return skip(controller, context);
                }
                // the implicit HEAD route has a key of its own: it is a route of its own
                String method0 = spec.implicitHead() ? spec.httpMethodName() + "~implicit" : spec.httpMethodName();
                int alias = aliases.merge(method0, 1, Integer::sum) - 1;
                routes.add(new RouteDescription(
                    RouteDescription.key(NAMESPACE, controller, method, method0, alias),
                    spec.httpMethodName(),
                    RouteTemplate.micronaut(spec.uri()),
                    new ControllerRoute(
                        controller.getName(),
                        method.getName(),
                        argumentTypes,
                        spec.declaringTypeTarget(),
                        names(spec.consumes()),
                        names(spec.produces()),
                        spec.implicitHead(),
                        spec.port()
                    ),
                    method
                ));
            }
        }
        return routes;
    }

    private static @Nullable List<RouteDescription> skip(ClassElement controller, VisitorContext context) {
        context.info("Routes of " + controller.getName() + " are not compiled: they use property placeholders", controller);
        return null;
    }

    private static boolean hasPlaceholder(String value) {
        return value.contains(PLACEHOLDER);
    }

    private static boolean hasPlaceholder(String[] values) {
        for (String value : values) {
            if (hasPlaceholder(value)) {
                return true;
            }
        }
        return false;
    }

    private static String @Nullable [] names(MediaType @Nullable [] mediaTypes) {
        if (mediaTypes == null) {
            return null;
        }
        String[] names = new String[mediaTypes.length];
        for (int i = 0; i < mediaTypes.length; i++) {
            names[i] = mediaTypes[i].toString();
        }
        return names;
    }
}
