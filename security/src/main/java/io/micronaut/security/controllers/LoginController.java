package io.micronaut.security.controllers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.AuthenticationSuccess;
import io.micronaut.security.authentication.Authenticator;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.config.JwtConfiguration;
import javax.inject.Singleton;

@Singleton
@Controller("/")
@Requires(property = JwtConfiguration.PREFIX + ".login", value = "true")
public class LoginController {

    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;
    protected final JwtConfiguration jwtConfiguration;
    protected final Authenticator authenticator;

    public LoginController(AccessRefreshTokenGenerator accessRefreshTokenGenerator, JwtConfiguration jwtConfiguration, Authenticator authenticator) {
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
        this.jwtConfiguration = jwtConfiguration;
        this.authenticator = authenticator;
    }

    @Post("/login")
    public HttpResponse login(@Body UsernamePassword usernamePassword) {
        AuthenticationResponse authenticationResponse = authenticator.authenticate(usernamePassword);
        if ( authenticationResponse instanceof AuthenticationSuccess) {
            return HttpResponse.ok().body(accessRefreshTokenGenerator.generate((AuthenticationSuccess)authenticationResponse));
        }
        return HttpResponse.status(HttpStatus.UNAUTHORIZED);
    }
}
