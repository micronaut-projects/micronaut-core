package io.micronaut.security.jwt;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Write;
import io.micronaut.security.*;

import java.util.Arrays;
import java.util.List;

@Endpoint("login")
public class LoginEndpoint {

    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;
    protected final JwtConfiguration jwtConfiguration;
    protected final Authenticator authenticator;
    public LoginEndpoint(AccessRefreshTokenGenerator accessRefreshTokenGenerator, JwtConfiguration jwtConfiguration, Authenticator authenticator) {
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
        this.jwtConfiguration = jwtConfiguration;
        this.authenticator = authenticator;
    }

    @Write
    public HttpResponse login(@Body UsernamePassword usernamePassword) {
        List<String> roles = Arrays.asList("ROLE_USER");
        AuthenticationResponse authenticationResponse = authenticator.authenticate(usernamePassword);
        if ( authenticationResponse instanceof UserDetails ) {
            return HttpResponse.ok().body(accessRefreshTokenGenerator.generate(new DefaultUserDetails(usernamePassword.getUsername(), roles)));
        }
        return HttpResponse.status(HttpStatus.UNAUTHORIZED);
    }
}
