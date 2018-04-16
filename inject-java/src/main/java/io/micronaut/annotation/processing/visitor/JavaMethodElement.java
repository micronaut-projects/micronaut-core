package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.visitor.MethodElement;

import javax.lang.model.element.ExecutableElement;

public class JavaMethodElement extends AbstractJavaElement implements MethodElement {

    private final ExecutableElement executableElement;

    JavaMethodElement(ExecutableElement executableElement) {
        super(executableElement);
        this.executableElement = executableElement;
    }


}
