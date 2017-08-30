package org.particleframework.http.binding.binders.request;

import org.particleframework.http.MediaType;
import org.particleframework.http.binding.annotation.Body;

/**
 * A binder that binds from a parsed request body
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BodyArgumentBinder<T> extends AnnotatedRequestArgumentBinder<Body, T> {

    /**
     * @return The required annotation type
     */
    @Override
    default Class<Body> getAnnotationType() {
        return Body.class;
    }

    /**
     * @return The required media type
     */
    MediaType getMediaType();

    /**
     * @return The argument type
     */
    Class<T> getArgumentType();
}
