package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class InterfaceGenVisitor implements TypeElementVisitor<InterfaceGen, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        context.visitGeneratedSourceFile(
            "test",
            "GeneratedInterface",
            element
        ).ifPresent(sourceFile -> {
            try {
                sourceFile.write(writer -> writer.write("""
                        package test;

                        import io.micronaut.context.annotation.Executable;

                        public interface GeneratedInterface {
                            @Executable
                            Bar test(Bar bar);
                        }
                        """));
            } catch (Exception e) {
                throw new ProcessingException(element, "Failed to generate a Parent interface: " + e.getMessage(), e);
            }
        });

        context.visitGeneratedSourceFile(
            "test",
            "Bar",
            element
        ).ifPresent(sourceFile -> {
            try {
                sourceFile.write(writer -> writer.write("""
                        package test;


                        public class Bar {
                        }
                        """));
            } catch (Exception e) {
                throw new ProcessingException(element, "Failed to generate a Parent interface: " + e.getMessage(), e);
            }
        });
    }

}
