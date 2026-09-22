package io.micronaut.test.routes.custom.processor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An annotation processor of a made-up web framework. It reads the framework's own annotations,
 * {@code @Resource}, {@code @Read} and {@code @Write}, and declares the routes of each resource
 * at compile time as an enum of {@code IndexedRouteDeclaration}s: one constant per annotated method,
 * with the HTTP method, the URI template and the keys the router indexes and orders routes by,
 * computed here once, and a generated URL parser, the enum's {@code CompiledRouteMatcher}, that
 * maps a request path to a constant. Handler functions are bound to the constants at runtime with
 * {@code routes.handle(PetResourceRoutes.NAME, handler)}; the router asks the parser first and
 * selects the bound route by the ordinal it answers.
 */
public final class CustomWebRoutesVisitor implements TypeElementVisitor<Object, Object> {

    static final String RESOURCE = "io.micronaut.test.routes.custom.annotation.Resource";
    static final String READ = "io.micronaut.test.routes.custom.annotation.Read";
    static final String WRITE = "io.micronaut.test.routes.custom.annotation.Write";

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
        List<String> constants = new ArrayList<>();
        CompiledRouteMatcherGenerator matcher = new CompiledRouteMatcherGenerator();
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
            // the ordinal of the constant is its position
            matcher.add(constants.size(), httpMethod, uri);
            constants.add(constant(method, httpMethod, uri));
        }
        String name = element.getSimpleName() + "Routes";
        String source = """
            package %s;

            /**
             * The routes of {@link %s}, declared at compile time from its web annotations.
             */
            public enum %s implements io.micronaut.web.router.spi.IndexedRouteDeclaration {
            %s;

                private final io.micronaut.http.HttpMethod httpMethod;
                private final String uriTemplate;
                private final String requiredPathPrefix;
                private final int rawLength;
                private final int pathVariableCount;

                %s(io.micronaut.http.HttpMethod httpMethod, String uriTemplate, String requiredPathPrefix, int rawLength, int pathVariableCount) {
                    this.httpMethod = httpMethod;
                    this.uriTemplate = uriTemplate;
                    this.requiredPathPrefix = requiredPathPrefix;
                    this.rawLength = rawLength;
                    this.pathVariableCount = pathVariableCount;
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
                public io.micronaut.web.router.spi.CompiledRouteMatcher matcher() {
                    return Matcher.INSTANCE;
                }

            %s}
            """.formatted(element.getPackageName(), element.getSimpleName(), name, String.join(",\n", constants), name, matcher.generate("Matcher"));
        GeneratedFile file = context.visitGeneratedSourceFile(element.getPackageName(), name, element)
            .orElseThrow(() -> new IllegalStateException("Cannot write the routes of " + element.getName()));
        try (Writer writer = file.openWriter()) {
            writer.write(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A constant for a method: the keys are computed like the router computes them for a route
     * built at runtime.
     */
    private static String constant(MethodElement method, String httpMethod, String uri) {
        UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(uri).getTemplateString());
        return "    " + NameUtils.environmentName(method.getName()).toUpperCase(Locale.ENGLISH)
            + "(io.micronaut.http.HttpMethod." + httpMethod + ", \"" + uri + "\", \"" + matcher.getRequiredPrefix() + "\", "
            + matcher.getRawLength() + ", " + matcher.getPathVariableCount() + ")";
    }
}
