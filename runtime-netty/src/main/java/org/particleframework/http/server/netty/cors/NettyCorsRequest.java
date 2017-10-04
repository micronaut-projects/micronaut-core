package org.particleframework.http.server.netty.cors;

import io.netty.handler.codec.http.HttpRequest;
import org.particleframework.http.cors.CorsRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps a Netty request to be used in CORS processing
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettyCorsRequest implements CorsRequest {

    private final HttpRequest request;

    public NettyCorsRequest(final HttpRequest request) {
        this.request = request;
    }

    @Override
    public String getRealMethod() {
        return request.method().name();
    }

    @Override
    public boolean hasHeader(String name) {
        return request.headers().contains(name);
    }

    @Override
    public String getHeader(final String name) {
        return request.headers().get(name);
    }

    @Override
    public List<String> getHeaders(final String name) {
        List<String> values = new ArrayList<>();
        for (String header: request.headers().getAll(name)) {
            if (header.contains(",")) {
                values.addAll(Arrays.stream(header.split(",")).map(String::trim).collect(Collectors.toList()));
            } else {
                values.add(header);
            }
        }
        return values;
    }

}
