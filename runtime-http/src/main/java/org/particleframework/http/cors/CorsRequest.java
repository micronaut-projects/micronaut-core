package org.particleframework.http.cors;

import org.particleframework.http.HttpMethod;
import java.util.List;

import static org.particleframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.particleframework.http.HttpHeaders.ORIGIN;

/**
 * Describes request object to be used in CORS processing
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface CorsRequest {

    default boolean isPreflightRequest() {
        return isCorsRequest() && hasHeader(ACCESS_CONTROL_REQUEST_METHOD) && HttpMethod.OPTIONS.equals(HttpMethod.valueOf(getRealMethod()));
    }

    default String getMethod() {
        if (isPreflightRequest()) {
            return getHeader(ACCESS_CONTROL_REQUEST_METHOD);
        } else {
            return getRealMethod();
        }
    }

    String getRealMethod();

    boolean hasHeader(final String name);

    String getHeader(final String name);

    /**
     * Must split out comma separated values
     * @param name The header name
     * @return A list of values
     */
    List<String> getHeaders(final String name);

    default boolean isCorsRequest() {
        return hasHeader(ORIGIN);
    }
}
