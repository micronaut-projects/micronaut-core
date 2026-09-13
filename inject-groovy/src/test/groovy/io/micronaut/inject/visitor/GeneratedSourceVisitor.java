package io.micronaut.inject.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates, as source, a singleton class next to each class annotated with {@link GenerateSource}.
 */
public class GeneratedSourceVisitor implements TypeElementVisitor<GenerateSource, Object> {

    private static List<String> visited = new ArrayList<>();

    public static List<String> getVisited() {
        return Collections.unmodifiableList(visited);
    }

    public static void clearVisited() {
        visited = new ArrayList<>();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visited.add(element.getName());
        boolean chain = element.booleanValue(GenerateSource.class, "chain").orElse(false);
        String body = element.stringValue(GenerateSource.class, "body").orElse("");
        String generatedName = element.getSimpleName() + "Generated";
        context.visitGeneratedSourceFile(element.getPackageName(), generatedName, element)
            .ifPresent(sourceFile -> {
                try {
                    sourceFile.write(writer -> writer.write("""
                        package %s

                        @jakarta.inject.Singleton
                        @io.micronaut.inject.visitor.GeneratedFrom("%s")
                        %s
                        class %s {
                            %s
                            String origin() { "%s" }
                        }
                        """.formatted(
                        element.getPackageName(),
                        element.getName(),
                        chain ? "@io.micronaut.inject.visitor.GenerateSource" : "",
                        generatedName,
                        body,
                        element.getName())));
                } catch (Exception e) {
                    throw new ProcessingException(element, "Failed to generate " + generatedName + ": " + e.getMessage(), e);
                }
            });
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
