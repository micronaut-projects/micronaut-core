package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.retry.annotation.Fallback;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.controllers.UsernamePassword;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;

import javax.inject.Singleton;
import java.util.Arrays;

@Fallback
@Singleton
public class TestLoginClientFallback implements LoginClient {

    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    public TestLoginClientFallback(AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }

    @Override
    public HttpResponse login(UsernamePassword usernamePassword) {
        UserDetails userDetails = null;
        if ( !usernamePassword.getPassword().equals("password") ) {
            return HttpResponse.unauthorized();
        }

        if ( usernamePassword.getUsername().equals("newton") ) {
            userDetails = new UserDetails("newton", Arrays.asList("ROLE_GROOVY"));
        } else if ( usernamePassword.getUsername().equals("euler") ) {
            userDetails = new UserDetails("euler", Arrays.asList("ROLE_GRAILS"));
        }
        return HttpResponse.ok().body(accessRefreshTokenGenerator.generate(userDetails));
    }
}
