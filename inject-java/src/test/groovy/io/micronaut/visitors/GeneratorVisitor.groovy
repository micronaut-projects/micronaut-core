package io.micronaut.visitors

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import org.intellij.lang.annotations.Language

class GeneratorVisitor implements TypeElementVisitor<GeneratorTrigger, Object> {

    private static final @Language("java") String SOURCE = """
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Introspected
public interface Parent {

    String BASE_PATH = "/hello";

    @Get("/get")
    TestModel hello();

}
"""

    private static final @Language("java") String SOURCE_MODEL = """
package example;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record TestModel(
        String greeting
) {

}
"""

    @Override
    void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(GeneratorTrigger)) {
            context.visitGeneratedSourceFile("example", "Parent", element)
                    .ifPresent(generatedFile -> {
                        try {
                            generatedFile.write(writer -> writer.write(SOURCE))
                        } catch (IOException e) {
                            throw new RuntimeException(e)
                        }
                    })

            context.visitGeneratedSourceFile("example", "TestModel", element)
                    .ifPresent(generatedFile -> {
                        try {
                            generatedFile.write(writer -> writer.write(SOURCE_MODEL))
                        } catch (IOException e) {
                            throw new RuntimeException(e)
                        }
                    })
        }

    }
}
