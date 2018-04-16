package io.micronaut.inject.visitor;

/**
 * Provides a way for {@link TypeElementVisitor} classes to
 * log messages during compilation and fail compilation.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface VisitorContext {

    void fail(String message, Element element);
}
