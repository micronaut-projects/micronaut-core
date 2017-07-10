package org.particleframework.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * <p>Common interface for HTTP request implementations</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpRequest {
    /**
     * @return The context path
     */
    String getContextPath();

    /**
     * @return The request method
     */
    HttpMethod getMethod();

    /**
     * @return The request URI
     */
    URI getUri();

    /**
     * @return The request content type
     */
    MediaType getContentType();
    /**
     * @return The request character encoding
     */
    Charset getCharacterEncoding();

    /**
     * @return The header for the request
     */
    Collection<String> getHeaderNames();

    /**
     * Obtains the value of a header
     *
     * @param name The name of the header
     * @return The value of the header
     */
    String getHeader(CharSequence name);

    /**
     * Obtains all the values for the give header
     *
     * @param name The name of the header
     * @return all of the views
     */
    Collection<String> getHeaderValues(String name);
}