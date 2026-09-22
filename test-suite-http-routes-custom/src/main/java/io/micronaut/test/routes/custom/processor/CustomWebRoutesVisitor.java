package io.micronaut.test.routes.custom.processor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.web.router.processor.CompiledRoutePlan;
import io.micronaut.web.router.processor.RouteDescription;
import io.micronaut.web.router.processor.RoutePlanCompiler;
import io.micronaut.web.router.spi.RouteSlot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An annotation processor of a made-up web framework. It reads the framework's own annotations,
 * {@code @Resource}, {@code @Read} and {@code @Write}, describes the routes of each resource to
 * the route compiler, which generates the route plan of the resource, {@code $PetResource$RoutePlan}:
 * the slot descriptors and a parser, generated with Sourcegen. The processor then declares the
 * routes as an enum of {@code PlannedRouteDeclaration}s, one constant per annotated method, each
 * the declaration of its slot, identified by its logical key. Handler functions are bound to the
 * constants at runtime with {@code routes.handle(PetResourceRoutes.NAME, handler)}; the router
 * asks the parser of the plan for the candidates of a request, and selects among them and the
 * other routes as usual.
 *
 * <p>The processor never parses a template itself: the compiler resolves the engine of each
 * template, and matches at runtime the templates it cannot compile.</p>
 */
public final class CustomWebRoutesVisitor implements TypeElementVisitor<Object, Object> {

    static final String RESOURCE = "io.micronaut.test.routes.custom.annotation.Resource";
    static final String READ = "io.micronaut.test.routes.custom.annotation.Read";
    static final String WRITE = "io.micronaut.test.routes.custom.annotation.Write";
    /**
     * The namespace of the keys of the routes of this framework.
     */
    static final String NAMESPACE = "resource";

    private final RoutePlanCompiler compiler = new RoutePlanCompiler();

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(RESOURCE);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasDeclaredAnnotation(RESOURCE)) {
            return;
        }
        String basePath = element.stringValue(RESOURCE).orElse("");
        List<RouteDescription> routes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
            String httpMethod;
            String uri;
            if (method.hasDeclaredAnnotation(READ)) {
                httpMethod = "GET";
                uri = basePath + method.stringValue(READ).orElse("");
            } else if (method.hasDeclaredAnnotation(WRITE)) {
                httpMethod = "POST";
                uri = basePath + method.stringValue(WRITE).orElse("");
            } else {
                continue;
            }
            routes.add(new RouteDescription(RouteDescription.key(NAMESPACE, element, method, httpMethod, 0), httpMethod,
                RouteTemplate.micronaut(uri), null, method));
            names.add(NameUtils.environmentName(method.getName()).toUpperCase(Locale.ENGLISH));
        }
        String planClass = element.getPackageName() + ".$" + element.getSimpleName() + "$RoutePlan";
        CompiledRoutePlan plan;
        try {
            // a plan of declarations: referenced by the declarations, not registered as a service
            plan = compiler.compile(planClass, NAMESPACE + ':' + element.getName(), List.of(), routes, false, context, element);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> constants = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            constants.add(constant(names.get(i), plan.slots().get(i)));
        }
        String name = element.getSimpleName() + "Routes";
        String source = """
            package %s;

            /**
             * The routes of {@link %s}, declared at compile time from its web annotations: the
             * declarations of the slots of its route plan.
             */
            public enum %s implements io.micronaut.web.router.spi.PlannedRouteDeclaration {
            %s;

                private final io.micronaut.http.HttpMethod httpMethod;
                private final String uriTemplate;
                private final String requiredPathPrefix;
                private final int rawLength;
                private final int pathVariableCount;
                private final String key;

                %s(io.micronaut.http.HttpMethod httpMethod, String uriTemplate, String requiredPathPrefix, int rawLength, int pathVariableCount, String key) {
                    this.httpMethod = httpMethod;
                    this.uriTemplate = uriTemplate;
                    this.requiredPathPrefix = requiredPathPrefix;
                    this.rawLength = rawLength;
                    this.pathVariableCount = pathVariableCount;
                    this.key = key;
                }

                @Override
                public io.micronaut.http.HttpMethod httpMethod() {
                    return httpMethod;
                }

                @Override
                public String uriTemplate() {
                    return uriTemplate;
                }

                @Override
                public String requiredPathPrefix() {
                    return requiredPathPrefix;
                }

                @Override
                public int rawLength() {
                    return rawLength;
                }

                @Override
                public int pathVariableCount() {
                    return pathVariableCount;
                }

                @Override
                public String key() {
                    return key;
                }

                @Override
                public io.micronaut.web.router.spi.RoutePlan plan() {
                    return Plan.INSTANCE;
                }

                /**
                 * The plan, created when first used.
                 */
                private static final class Plan {
                    static final io.micronaut.web.router.spi.RoutePlan INSTANCE = new %s();
                }
            }
            """.formatted(element.getPackageName(), element.getSimpleName(), name, String.join(",\n", constants), name, planClass);
        GeneratedFile file = context.visitGeneratedSourceFile(element.getPackageName(), name, element)
            .orElseThrow(() -> new IllegalStateException("Cannot write the routes of " + element.getName()));
        try (Writer writer = file.openWriter()) {
            writer.write(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A constant for a slot, with the facts the compiler computed for its template.
     */
    private static String constant(String name, RouteSlot slot) {
        return "    " + name + "(io.micronaut.http.HttpMethod." + slot.httpMethodName() + ", \"" + slot.template().expression() + "\", \""
            + slot.requiredPrefix() + "\", " + slot.rawLength() + ", " + slot.pathVariableCount() + ", \"" + slot.key() + "\")";
    }
}
