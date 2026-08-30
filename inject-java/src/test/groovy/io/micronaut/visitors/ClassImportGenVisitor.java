package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class ClassImportGenVisitor implements TypeElementVisitor<GenerateImporter, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        context.visitGeneratedSourceFile("test", "GeneratedImporter", element)
            .ifPresent(sourceFile -> {
                try {
                    sourceFile.write(writer -> writer.write("""
                        package test;

                        @io.micronaut.context.annotation.ClassImport(
                            classes = io.micronaut.visitors.MyImportedBean.class,
                            annotate = jakarta.inject.Singleton.class)
                        final class GeneratedImporter {
                        }
                        """));
                } catch (Exception e) {
                    throw new ProcessingException(element, "Failed to generate the importer: " + e.getMessage(), e);
                }
            });
    }

}
