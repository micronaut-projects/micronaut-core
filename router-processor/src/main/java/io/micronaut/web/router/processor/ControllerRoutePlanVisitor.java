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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.annotation.Controller;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Compiles the routes of each controller of the compilation into a route plan of its own,
 * {@code $<Controller>$RoutePlan}, registered as a service of
 * {@link io.micronaut.web.router.spi.RoutePlan}, with its descriptor. The plan originates from
 * the controller alone: the output of a controller is written when the controller is compiled,
 * and is deleted with it.
 *
 * <p>The router uses the plan of a controller when the application uses the default URI naming
 * strategy and no context path, like the routes are derived here; otherwise the routes of the
 * controller are derived at runtime.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ControllerRoutePlanVisitor implements TypeElementVisitor<Controller, Object> {

    private final RoutePlanCompiler compiler = new RoutePlanCompiler();

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(Controller.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!ControllerRoutes.isController(element)) {
            return;
        }
        List<RouteDescription> routes = ControllerRoutes.describe(element, context);
        if (routes == null) {
            return;
        }
        compile(compiler, element, routes, context);
    }

    private static void compile(RoutePlanCompiler compiler, ClassElement controller, List<RouteDescription> routes, VisitorContext context) {
        try {
            compiler.compile(ControllerRoutes.planClassName(controller), ControllerRoutes.planId(controller), List.of(controller.getName()),
                routes, true, context, controller);
        } catch (IOException e) {
            context.fail("Failed to write the route plan of " + controller.getName() + ": " + e.getMessage(), controller);
        } catch (RuntimeException e) {
            context.fail("Cannot compile the routes of " + controller.getName() + ": " + message(e), controller);
        }
    }

    private static @Nullable String message(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
