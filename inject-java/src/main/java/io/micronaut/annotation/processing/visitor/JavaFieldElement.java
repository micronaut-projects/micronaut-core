package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.visitor.FieldElement;

import javax.lang.model.element.VariableElement;

public class JavaFieldElement extends AbstractJavaElement implements FieldElement {

    private final VariableElement variableElement;

    JavaFieldElement(VariableElement variableElement) {
        super(variableElement);
        this.variableElement = variableElement;
    }

}
