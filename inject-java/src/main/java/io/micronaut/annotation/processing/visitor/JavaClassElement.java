package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.visitor.ClassElement;

import javax.lang.model.element.TypeElement;

public class JavaClassElement extends AbstractJavaElement implements ClassElement {

    private final TypeElement classElement;

    JavaClassElement(TypeElement classElement) {
        super(classElement);
        this.classElement = classElement;
    }

    @Override
    public String getName() {
        return classElement.getQualifiedName().toString();
    }
}
