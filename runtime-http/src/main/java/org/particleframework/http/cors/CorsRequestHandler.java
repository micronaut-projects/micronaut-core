package org.particleframework.http.cors;

/**
 * Functional interface that provides actions to take during
 * CORS request processing
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface CorsRequestHandler {

    void rejectRequest();

    void preflightSuccess();

    void continueRequest();
}
