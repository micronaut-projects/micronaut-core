package io.micronaut.annotation.processing.visitor;

import javax.lang.model.element.Element;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

public class JavaVisitorContext implements VisitorContext {

    private final Messager messager;

    public JavaVisitorContext(Messager messager) {
        this.messager = messager;
    }

    @Override
    public void fail(String message, io.micronaut.inject.visitor.Element element) {
        Element el = (Element) element.getNativeType();
        messager.printMessage(Diagnostic.Kind.ERROR, message, el);
    }
}
