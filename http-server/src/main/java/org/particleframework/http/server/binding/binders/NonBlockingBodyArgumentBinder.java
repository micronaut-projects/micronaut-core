package org.particleframework.http.server.binding.binders;

/**
 * A marker interface for argument binders that are non-blocking
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface NonBlockingBodyArgumentBinder<T> extends BodyArgumentBinder<T>, TypedRequestArgumentBinder<T> {
}
