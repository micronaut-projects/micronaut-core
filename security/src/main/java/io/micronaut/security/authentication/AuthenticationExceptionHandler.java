package io.micronaut.security.authentication;

import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateos.Link;
import io.micronaut.http.hateos.VndError;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;

@Singleton
@Primary
@Produces
public class AuthenticationExceptionHandler implements ExceptionHandler<AuthenticationException, HttpResponse> {

    @Override
    public HttpResponse handle(HttpRequest request, AuthenticationException exception) {
        VndError error = new VndError(exception.getMessage());
        error.link(Link.SELF, Link.of(request.getUri()));
        return HttpResponse.unauthorized().body(error);
    }
}

