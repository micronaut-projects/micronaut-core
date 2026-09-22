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
package io.micronaut.validation.routes;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.UriMapping;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.web.router.PrecompiledHttpRoutesDefinition;
import io.micronaut.web.router.PrecompiledRoute;
import io.micronaut.web.router.RouteDefinitions;
import io.micronaut.web.router.annotation.PrecompiledHttpRoutes;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a {@link PrecompiledHttpRoutesDefinition} with the routes of every controller of the
 * compilation, and of the packages listed on {@link PrecompiledHttpRoutes}, for the class
 * annotated with {@link PrecompiledHttpRoutes}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class PrecompiledHttpRoutesVisitor implements TypeElementVisitor<Object, Object> {

    private static final String CLASS_SUFFIX = "$PrecompiledHttpRoutes";
    private static final String PLACEHOLDER = "${";
    private static final ClassTypeDef ROUTE_TYPE = ClassTypeDef.of(PrecompiledRoute.class);
    /**
     * The canonical constructor, which types the {@code null} arguments of the generated calls.
     */
    private static final Constructor<?> ROUTE_CONSTRUCTOR = PrecompiledRoute.class.getConstructors()[0];
    /**
     * The naming strategy the runtime uses with precompiled routes.
     */
    private static final HyphenatedUriNamingStrategy NAMING_STRATEGY = new HyphenatedUriNamingStrategy();

    private final Map<String, ClassElement> controllers = new LinkedHashMap<>();
    private @Nullable ClassElement annotated;
    private boolean written;

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
            PrecompiledHttpRoutes.class.getName(),
            Controller.class.getName(),
            HttpMethodMapping.class.getName()
        );
    }

    @Override
    public void start(VisitorContext visitorContext) {
        controllers.clear();
        annotated = null;
        written = false;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(PrecompiledHttpRoutes.class)) {
            if (annotated != null && !annotated.getName().equals(element.getName())) {
                context.fail("Only one class can be annotated with @PrecompiledHttpRoutes, found " + annotated.getName() + " and " + element.getName(), element);
                return;
            }
            annotated = element;
        }
        if (isController(element)) {
            if (written) {
                context.info("Routes of " + element.getName() + " are not precompiled: the controller was generated after the precompiled routes were written", element);
            } else {
                controllers.putIfAbsent(element.getName(), element);
            }
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        ClassElement target = annotated;
        if (written || target == null) {
            return;
        }
        written = true;
        for (String aPackage : target.stringValues(PrecompiledHttpRoutes.class, "packages")) {
            for (ClassElement element : visitorContext.getClassElements(aPackage, Controller.class.getName())) {
                if (isController(element)) {
                    controllers.putIfAbsent(element.getName(), element);
                }
            }
        }
        List<String> controllerTypes = new ArrayList<>();
        List<PrecompiledRoute> routes = new ArrayList<>();
        List<Element> originatingElements = new ArrayList<>();
        originatingElements.add(target);
        for (ClassElement controller : controllers.values()) {
            List<PrecompiledRoute> controllerRoutes = precompile(controller, visitorContext);
            if (controllerRoutes != null) {
                controllerTypes.add(controller.getName());
                routes.addAll(controllerRoutes);
                originatingElements.add(controller);
            }
        }
        String className = target.getPackageName() + ".$" + target.getSimpleName() + CLASS_SUFFIX;
        try {
            write(className, controllerTypes, routes, originatingElements.toArray(Element[]::new), visitorContext);
        } catch (IOException e) {
            visitorContext.fail("Failed to write the precompiled routes: " + e.getMessage(), target);
        }
    }

    private static boolean isController(ClassElement element) {
        return element.hasStereotype(Controller.class) && !element.isAbstract() && !element.isInterface();
    }

    /**
     * Derive the routes of a controller the same way the runtime route builder does.
     *
     * @param controller The controller
     * @param context    The visitor context
     * @return The routes, or {@code null} if the controller cannot be precompiled
     */
    private static @Nullable List<PrecompiledRoute> precompile(ClassElement controller, VisitorContext context) {
        String controllerUri = controller.stringValue(UriMapping.class)
            .orElseGet(() -> controller.stringValue(Controller.class).orElse(UriMapping.DEFAULT_URI));
        if (hasPlaceholder(controllerUri) || controller.stringValue(Controller.class, "port").filter(PrecompiledHttpRoutesVisitor::hasPlaceholder).isPresent()) {
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
        List<PrecompiledRoute> routes = new ArrayList<>();
        for (MethodElement method : controller.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
            if (!method.hasStereotype(Executable.class)
                || !method.hasStereotype(HttpMethodMapping.class) && !method.hasDeclaredAnnotation(UriMapping.class)) {
                // the runtime only routes the executable methods of a controller
                continue;
            }
            if (hasPlaceholder(method.stringValues(HttpMethodMapping.class, "uris"))
                || method.stringValue(HttpMethodMapping.class).filter(PrecompiledHttpRoutesVisitor::hasPlaceholder).isPresent()
                || hasPlaceholder(method.stringValues(UriMapping.class, "uris"))
                || method.stringValue(UriMapping.class).filter(PrecompiledHttpRoutesVisitor::hasPlaceholder).isPresent()
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
                .map(PrecompiledHttpRoutesVisitor::erasedName)
                .toArray(String[]::new);
            for (RouteDefinitions.RouteSpec spec : specs) {
                if (hasPlaceholder(spec.uri())) {
                    return skip(controller, context);
                }
                UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(spec.uri()).getTemplateString());
                routes.add(new PrecompiledRoute(
                    controller.getName(),
                    method.getName(),
                    argumentTypes,
                    spec.httpMethodName(),
                    spec.httpMethod().name(),
                    spec.uri(),
                    names(spec.consumes()),
                    names(spec.produces()),
                    spec.implicitHead(),
                    spec.port(),
                    spec.declaringTypeTarget(),
                    matcher.getRequiredPrefix(),
                    matcher.getRawLength(),
                    matcher.getPathVariableCount()
                ));
            }
        }
        return routes;
    }

    private static @Nullable List<PrecompiledRoute> skip(ClassElement controller, VisitorContext context) {
        context.info("Routes of " + controller.getName() + " are not precompiled: they use property placeholders", controller);
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

    /**
     * The name of the erased parameter type, as {@link Class#getName()} returns it at runtime.
     *
     * @param parameter The parameter
     * @return The name
     */
    private static String erasedName(ParameterElement parameter) {
        ClassElement type = parameter.getType();
        if (!type.isArray()) {
            return type.getName();
        }
        StringBuilder name = new StringBuilder();
        name.append("[".repeat(type.getArrayDimensions()));
        ClassElement component = type.fromArray();
        while (component.isArray()) {
            component = component.fromArray();
        }
        if (component.isPrimitive()) {
            name.append(switch (component.getName()) {
                case "boolean" -> 'Z';
                case "byte" -> 'B';
                case "char" -> 'C';
                case "short" -> 'S';
                case "int" -> 'I';
                case "long" -> 'J';
                case "float" -> 'F';
                case "double" -> 'D';
                default -> throw new IllegalStateException("Unknown primitive type: " + component.getName());
            });
        } else {
            name.append('L').append(component.getName()).append(';');
        }
        return name.toString();
    }

    private static void write(String className,
                              List<String> controllerTypes,
                              List<PrecompiledRoute> routes,
                              Element[] originatingElements,
                              VisitorContext context) throws IOException {
        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(className)
            .synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", PrecompiledHttpRoutesDefinition.class.getName()).build())
            .addSuperinterface(ClassTypeDef.of(PrecompiledHttpRoutesDefinition.class));

        List<ExpressionDef> controllerNames = controllerTypes.stream().map(name -> (ExpressionDef) ExpressionDef.constant(name)).toList();
        classDefBuilder.addMethod(MethodDef.override(ReflectionUtils.getRequiredMethod(PrecompiledHttpRoutesDefinition.class, "controllerTypes"))
            .build((aThis, methodParameters) -> TypeDef.STRING.array().instantiate(controllerNames).returning()));

        // a method per route keeps every method far below the bytecode size limit
        List<MethodDef> routeMethods = new ArrayList<>(routes.size());
        for (int i = 0; i < routes.size(); i++) {
            PrecompiledRoute route = routes.get(i);
            MethodDef routeMethod = MethodDef.builder("$route" + i)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ROUTE_TYPE)
                .build((aThis, methodParameters) -> ROUTE_TYPE.instantiate(
                    ROUTE_CONSTRUCTOR,
                    ExpressionDef.constant(route.controllerType()),
                    ExpressionDef.constant(route.methodName()),
                    stringArray(route.argumentTypes()),
                    ExpressionDef.constant(route.httpMethodName()),
                    ExpressionDef.constant(route.httpMethod()),
                    ExpressionDef.constant(route.uri()),
                    stringArray(route.consumes()),
                    stringArray(route.produces()),
                    ExpressionDef.constant(route.implicitHead()),
                    ExpressionDef.constant(route.port()),
                    ExpressionDef.constant(route.declaringTypeTarget()),
                    ExpressionDef.constant(route.requiredPathPrefix()),
                    ExpressionDef.constant(route.rawLength()),
                    ExpressionDef.constant(route.pathVariableCount())
                ).returning());
            routeMethods.add(routeMethod);
            classDefBuilder.addMethod(routeMethod);
        }
        ClassTypeDef thisType = ClassTypeDef.of(className);
        classDefBuilder.addMethod(MethodDef.override(ReflectionUtils.getRequiredMethod(PrecompiledHttpRoutesDefinition.class, "routes"))
            .build((aThis, methodParameters) -> ROUTE_TYPE.array().instantiate(
                routeMethods.stream().map(thisType::invokeStatic).toList()
            ).returning()));

        try (OutputStream outputStream = context.visitClass(className, originatingElements)) {
            outputStream.write(ByteCodeWriterUtils.writeByteCode(classDefBuilder.build(), context));
        }
        context.visitServiceDescriptor(PrecompiledHttpRoutesDefinition.class.getName(), className, originatingElements[0]);
    }

    private static ExpressionDef stringArray(String @Nullable [] values) {
        if (values == null) {
            return ExpressionDef.nullValue();
        }
        return TypeDef.STRING.array().instantiate(Arrays.stream(values).map(value -> (ExpressionDef) ExpressionDef.constant(value)).toList());
    }
}
