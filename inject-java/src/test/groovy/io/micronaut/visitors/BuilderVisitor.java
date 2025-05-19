package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class BuilderVisitor implements TypeElementVisitor<Builder, Object> {

    @Override
    public TypeElementQuery query() {
        return TypeElementVisitor.super.query().skipUnresolvedInterfaces();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        element.getAllTypeArguments();
        context.visitGeneratedSourceFile(
            "test",
            "WalrusBuilder",
            element
        ).ifPresent(sourceFile -> {
            try {
                sourceFile.write(writer -> writer.write("""
                        package test;

                        public class WalrusBuilder {
                            private String name;

                            private int age;

                            private byte[] chipInfo;

                            public WalrusBuilder name(String name) {
                                this.name = name;
                                return this;
                            }

                            public WalrusBuilder age(int age) {
                                this.age = age;
                                return this;
                            }

                            public WalrusBuilder chipInfo(byte[] chipInfo) {
                                this.chipInfo = chipInfo;
                                return this;
                            }

                            public Walrus build() {
                                return new Walrus(this.name, this.age, this.chipInfo);
                            }
                        }
                        """));
            } catch (Exception e) {
                throw new ProcessingException(element, "Failed to generate a Builder: " + e.getMessage(), e);
            }
        });
    }

}
