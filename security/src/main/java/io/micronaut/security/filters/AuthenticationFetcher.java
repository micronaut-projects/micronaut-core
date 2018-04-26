package io.micronaut.security.filters;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;

import java.util.Optional;

public interface AuthenticationFetcher extends Ordered {

    Optional<Authentication> fetchAuthentication(HttpRequest<?> request);
}
