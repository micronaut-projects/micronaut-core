package io.micronaut.annotation.processing.visitor;

import jakarta.annotation.Nullable;

import javax.lang.model.element.Element;

/**
 * A provider of an element.
 */
public interface ElementProvider {
        /**
     * @return The native element.
     */
    @Nullable
    Element element();
}
