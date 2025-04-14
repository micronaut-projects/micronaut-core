package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class IntroductionTestVisitor implements TypeElementVisitor<IntroductionTestGen, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
            context.visitGeneratedSourceFile(
                "test",
                "IntroductionTestParent",
                element
            ).ifPresent(sourceFile -> {
                try {
                    sourceFile.write(writer -> writer.write("""
                        package test;

                        public interface IntroductionTestParent {
                            String getParentMethod();
                        }
                        """));
                } catch (Exception e) {
                    throw new ProcessingException(element, "Failed to generate a Parent introduction: " + e.getMessage(), e);
                }
            });
    }

}
