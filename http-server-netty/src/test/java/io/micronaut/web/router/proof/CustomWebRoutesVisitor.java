package io.micronaut.web.router.proof;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An annotation processor of a made-up web framework. It reads the framework's own annotations,
 * {@code @Resource}, {@code @Read}, {@code @Write}, {@code @Param} and {@code @Field}, and writes
 * the routes of each resource at compile time: a {@code @Singleton} {@code HttpRoutes} bean whose
 * handler functions call the resource bean's methods directly, with the arguments taken from the
 * path variables and the form. Nothing is read by reflection at runtime, and the resource needs no
 * executable methods.
 */
public final class CustomWebRoutesVisitor implements TypeElementVisitor<Object, Object> {

    static final String RESOURCE = "petstore.web.Resource";
    static final String READ = "petstore.web.Read";
    static final String WRITE = "petstore.web.Write";
    static final String PARAM = "petstore.web.Param";
    static final String FIELD = "petstore.web.Field";

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
        List<String> routes = new ArrayList<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
            if (method.hasDeclaredAnnotation(READ)) {
                routes.add(readRoute(basePath + method.stringValue(READ).orElse(""), method));
            } else if (method.hasDeclaredAnnotation(WRITE)) {
                routes.add(writeRoute(basePath + method.stringValue(WRITE).orElse(""), method));
            }
        }
        String routesName = element.getSimpleName() + "Routes";
        // a plain source bean: a class annotated @Generated is not processed into a bean definition
        String source = """
            package %s;

            @jakarta.inject.Singleton
            final class %s implements io.micronaut.web.router.HttpRoutes {
                private final io.micronaut.context.BeanProvider<%s> target;

                %s(io.micronaut.context.BeanProvider<%s> target) {
                    this.target = target;
                }

                @Override
                public void routes(io.micronaut.web.router.RouteBuilder routes) {
            %s
                }
            }
            """.formatted(element.getPackageName(), routesName, element.getSimpleName(), routesName, element.getSimpleName(), String.join("\n", routes));
        GeneratedFile file = context.visitGeneratedSourceFile(element.getPackageName(), routesName, element)
            .orElseThrow(() -> new IllegalStateException("Cannot write the routes of " + element.getName()));
        try (Writer writer = file.openWriter()) {
            writer.write(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readRoute(String uri, MethodElement method) {
        List<String> arguments = new ArrayList<>();
        for (ParameterElement parameter : method.getParameters()) {
            arguments.add(accessor("pathVariables", parameter.stringValue(PARAM).orElse(parameter.getName()), parameter));
        }
        return "        routes.GET(\"" + uri + "\", (request, pathVariables) -> " + respond(method, arguments) + ");";
    }

    private static String writeRoute(String uri, MethodElement method) {
        List<String> arguments = new ArrayList<>();
        for (ParameterElement parameter : method.getParameters()) {
            if (parameter.hasDeclaredAnnotation(PARAM)) {
                arguments.add(accessor("pathVariables", parameter.stringValue(PARAM).orElseThrow(), parameter));
            } else {
                arguments.add(accessor("form", parameter.stringValue(FIELD).orElse(parameter.getName()), parameter));
            }
        }
        return "        routes.POST(\"" + uri + "\", (request, pathVariables, form) -> " + respond(method, arguments) + ");";
    }

    /**
     * The call of the resource method, and the response: 204 for a void method, 200 with the
     * result as the body otherwise.
     */
    private static String respond(MethodElement method, List<String> arguments) {
        String call = "target.get()." + method.getName() + "(" + String.join(", ", arguments) + ")";
        if (method.getReturnType().getName().equals("void")) {
            return "{ " + call + "; return io.micronaut.http.HttpResponse.noContent(); }";
        }
        return "io.micronaut.http.HttpResponse.ok(" + call + ")";
    }

    /**
     * The typed accessor of the value of a parameter: a primitive or string getter, or the
     * generic one for any other type.
     */
    private static String accessor(String source, String name, ParameterElement parameter) {
        String type = parameter.getType().getName();
        String getter = switch (type) {
            case "java.lang.String" -> "getString";
            case "int" -> "getInt";
            case "long" -> "getLong";
            case "double" -> "getDouble";
            case "boolean" -> "getBoolean";
            default -> null;
        };
        if (getter != null) {
            return source + "." + getter + "(\"" + name + "\")";
        }
        return source + ".get(\"" + name + "\", " + type + ".class)";
    }
}
