package org.particleframework.http;


/**
 * <p>Common interface for HTTP response implementations</p>
 *
 * @since 1.0
 * @author Graeme Rocher
 */
public interface HttpResponse<B> extends HttpMessage<B> {
    /**
     * @return The headers for the response
     */
    @Override
    MutableHttpHeaders getHeaders();

    /**
     * @return The current status
     */
    HttpStatus getStatus();

    /**
     * Return an {@link HttpStatus#OK} response with an empty body
     *
     * @return The ok response
     */
    static MutableHttpResponse ok() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE;
        if( factory == null) {
            throw new IllegalStateException("No Server implementation found on classpath");
        }
        return factory.ok();
    }


    /**
     * Return an {@link HttpStatus#OK} response with an empty body
     *
     * @return The ok response
     */
    static <T> MutableHttpResponse<T> ok(T body) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE;
        if( factory == null) {
            throw new IllegalStateException("No Server implementation found on classpath");
        }
        return factory.ok(body);
    }

    /**
     * Return a response for the given status
     *
     * @param status The status
     * @return The response
     */
    static MutableHttpResponse status(HttpStatus status) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE;
        if( factory == null) {
            throw new IllegalStateException("No Server implementation found on classpath");
        }
        return factory.status(status);
    }

    /**
     * Return a response for the given status
     *
     * @param status The status
     * @param reason An alternatively reason message
     * @return The response
     */
    static MutableHttpResponse status(HttpStatus status, String reason) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE;
        if( factory == null) {
            throw new IllegalStateException("No Server implementation found on classpath");
        }
        return factory.status(status, reason);
    }
}