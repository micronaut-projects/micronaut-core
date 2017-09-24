package org.particleframework.http.server.netty.cors;

import io.netty.handler.codec.http.HttpResponse;
import org.particleframework.http.cors.CorsResponse;

/**
 * Wraps a Netty response to be used in CORS processing
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettyCorsResponse implements CorsResponse {

    private final HttpResponse response;

    public NettyCorsResponse(final HttpResponse response) {
        this.response = response;
    }

    @Override
    public void setHeader(String name, String value) {
        response.headers().set(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        response.headers().add(name, value);
    }
}
