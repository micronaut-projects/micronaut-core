package demo.services;

import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;

import javax.inject.Singleton;
import java.util.ArrayList;

@Singleton
public class AuthenticationProviderUserPassword implements AuthenticationProvider  {
    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        if ( authenticationRequest.getIdentity().equals("sherlock") &&
                authenticationRequest.getSecret().equals("password") ) {
            return new UserDetails((String) authenticationRequest.getIdentity(), new ArrayList<>());
        }
        return new AuthenticationFailed();
    }
}
