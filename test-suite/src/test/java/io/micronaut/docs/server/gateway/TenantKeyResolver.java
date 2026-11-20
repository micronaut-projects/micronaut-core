package io.micronaut.docs.server.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.gateway.KeyResolver;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
@Named("tenant")
public class TenantKeyResolver implements KeyResolver {

    @Override
    public @Nullable String resolve(HttpRequest<?> request) {
        // synchronous: read the request, no IO
        return request.getHeaders().get("X-Tenant");
    }
}
// end::clazz[]
