package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.AbstractJavaElement;
import io.micronaut.inject.ast.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.MethodElement;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

public class JavaElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<Element, AnnotationMirror> {

    public JavaElementAnnotationMetadataFactory(boolean isReadOnly, JavaAnnotationMetadataBuilder metadataBuilder) {
        super(isReadOnly, metadataBuilder);
    }

    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new JavaElementAnnotationMetadataFactory(true, (JavaAnnotationMetadataBuilder) metadataBuilder);
    }

    @Override
    protected boolean isSupported(MethodElement methodElement) {
        return methodElement instanceof AbstractJavaElement;
    }
}
