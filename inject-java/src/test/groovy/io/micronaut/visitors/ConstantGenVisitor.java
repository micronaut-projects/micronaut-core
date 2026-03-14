package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.intellij.lang.annotations.Language;

import java.io.IOException;


public class ConstantGenVisitor implements TypeElementVisitor<ConstantGen, Object> {

    private static final @Language("java") String SOURCE_MODEL = """
package test;

class SomeInterfaceConstants {
    public static final String PATH = "generated";
}
""";

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(ConstantGen.class)) {
            context.visitGeneratedSourceFile("test", "SomeInterfaceConstants", element)
                .ifPresent(generatedFile -> {
                    try {
                        generatedFile.write(writer -> writer.write(SOURCE_MODEL));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
