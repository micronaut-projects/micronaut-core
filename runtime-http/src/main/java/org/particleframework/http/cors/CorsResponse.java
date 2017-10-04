package org.particleframework.http.cors;

/**
 * Describes response object to be used in CORS processing
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface CorsResponse {

    void setHeader(final String name, final String value);

    void addHeader(final String name, final String value);
}
