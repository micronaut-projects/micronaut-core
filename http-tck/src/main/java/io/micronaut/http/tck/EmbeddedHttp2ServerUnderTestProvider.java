package io.micronaut.http.tck;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

@Experimental
public class EmbeddedHttp2ServerUnderTestProvider implements ServerUnderTestProvider {
    @Override
    public @NonNull ServerUnderTest getServer(Map<String, Object> properties) {
        Map<String, Object> mod = new HashMap<>(properties);
        mod.put("micronaut.server.ssl.enabled", true);
        mod.put("micronaut.server.ssl.build-self-signed", true);
        mod.put("micronaut.server.http-version", "2.0");
        mod.put("micronaut.http.client.http-version", "2.0");
        mod.put("micronaut.http.client.ssl.insecure-trust-all-certificates", true);
        return new EmbeddedServerUnderTest(mod);
    }
}
