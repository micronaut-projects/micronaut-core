package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;

/**
 * Provides a hook into the compilation process to allow user
 * defined functionality to be created at compile time.
 *
 * @param <C> The annotation required on the class.
 * @param <E> The annotation required on the element.
 * @author James Kleeh
 * @since 1.0
 */
public interface TypeElementVisitor<C, E> {

    /**
     * Executed when a class is encountered that matches the <C> generic
     *
     * @param element The element
     * @param annotationMetadata The annotation metadata
     * @param context The visitor context
     */
    void visitClass(ClassElement element, AnnotationMetadata annotationMetadata, VisitorContext context);

    /**
     * Executed when a method is encountered that matches the <E> generic
     *
     * @param element The element
     * @param annotationMetadata The annotation metadata
     * @param context The visitor context
     */
    void visitMethod(MethodElement element, AnnotationMetadata annotationMetadata, VisitorContext context);

    /**
     * Executed when a field is encountered that matches the <E> generic
     *
     * @param element The element
     * @param annotationMetadata The annotation metadata
     * @param context The visitor context
     */
    void visitField(FieldElement element, AnnotationMetadata annotationMetadata, VisitorContext context);
}
