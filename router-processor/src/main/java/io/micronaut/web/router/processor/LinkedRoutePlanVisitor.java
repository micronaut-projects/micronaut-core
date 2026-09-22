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
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Links the controllers of compiled libraries into one route plan of the compilation, when the
 * build asks for it with annotation processor options: the controllers of the packages of
 * {@value #PACKAGES}, found on the compilation class path, are compiled into the plan
 * {@value #CLASS_NAME}. The controllers of the compilation itself have plans of their own, see
 * {@link ControllerRoutePlanVisitor}.
 *
 * <pre>
 * -Amicronaut.router.link.packages=example.library,example.library.admin
 * -Amicronaut.router.link.class=example.app.$LinkedRoutePlan
 * </pre>
 *
 * <p>This is an explicit linking step for libraries compiled without the route compiler; it is
 * not the whole-application linker, which would take the per-origin descriptors of every module
 * as input.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class LinkedRoutePlanVisitor implements TypeElementVisitor<Object, Object> {

    /**
     * The option with the packages of the controllers to link, separated by commas.
     */
    public static final String PACKAGES = "micronaut.router.link.packages";
    /**
     * The option with the name of the class of the linked plan.
     */
    public static final String CLASS_NAME = "micronaut.router.link.class";

    private boolean written;

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(PACKAGES, CLASS_NAME);
    }

    @Override
    public void start(VisitorContext visitorContext) {
        written = false;
    }

    @Override
    public void finish(VisitorContext context) {
        String packages = context.getOptions().get(PACKAGES);
        if (written || packages == null || packages.isBlank()) {
            return;
        }
        written = true;
        String className = context.getOptions().get(CLASS_NAME);
        if (className == null || className.isBlank()) {
            context.fail("The option " + CLASS_NAME + " names the class of the route plan linked from the packages of " + PACKAGES, null);
            return;
        }
        Map<String, ClassElement> controllers = new LinkedHashMap<>();
        for (String aPackage : packages.split(",")) {
            for (ClassElement element : context.getClassElements(aPackage.trim(), Controller.class.getName())) {
                if (ControllerRoutes.isController(element)) {
                    controllers.putIfAbsent(element.getName(), element);
                }
            }
        }
        List<String> owners = new ArrayList<>();
        List<RouteDescription> routes = new ArrayList<>();
        List<Element> originatingElements = new ArrayList<>();
        for (ClassElement controller : controllers.values()) {
            List<RouteDescription> controllerRoutes = ControllerRoutes.describe(controller, context);
            if (controllerRoutes != null) {
                owners.add(controller.getName());
                routes.addAll(controllerRoutes);
                originatingElements.add(controller);
            }
        }
        if (originatingElements.isEmpty()) {
            context.warn("No controller to link in the packages " + packages, null);
            return;
        }
        try {
            new RoutePlanCompiler().compile(className, "linked:" + className, owners, routes, true, context,
                originatingElements.toArray(Element[]::new));
        } catch (IOException e) {
            context.fail("Failed to write the linked route plan " + className + ": " + e.getMessage(), null);
        } catch (RuntimeException e) {
            context.fail("Cannot link the routes of the packages " + packages + ": " + e.getMessage(), null);
        }
    }
}
