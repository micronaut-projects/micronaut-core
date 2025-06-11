package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class WitherVisitor implements TypeElementVisitor<Wither, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
            context.visitGeneratedSourceFile(
                "test",
                "WalrusWither",
                element
            ).ifPresent(sourceFile -> {
                try {
                    sourceFile.write(writer -> writer.write("""
                        package test;

                        public interface WalrusWither {
                            String name();

                            int age();

                            byte[] chipInfo();

                            default Walrus withName(String name) {
                                return new Walrus(name, this.age(), this.chipInfo());
                            }

                            default Walrus withAge(int age) {
                                return new Walrus(this.name(), age, this.chipInfo());
                            }

                            default Walrus withChipInfo(byte[] chipInfo) {
                                return new Walrus(this.name(), this.age(), chipInfo);
                            }
                        }
                        """));
                } catch (Exception e) {
                    throw new ProcessingException(element, "Failed to generate a Wither: " + e.getMessage(), e);
                }
            });
    }

}
