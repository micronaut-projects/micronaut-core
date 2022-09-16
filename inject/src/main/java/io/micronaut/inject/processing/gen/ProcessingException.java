package io.micronaut.inject.processing.gen;

import io.micronaut.inject.ast.Element;

public class ProcessingException extends RuntimeException {

    private final Element element;
    private final String message;

    public ProcessingException(Element element, String message) {
        this.element = element;
        this.message = message;
    }

    public Element getElement() {
        return element;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
