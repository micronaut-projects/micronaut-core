package example.security;

import example.security.gateway.LoginClient;
import io.micronaut.http.HttpResponse;
import io.micronaut.retry.annotation.Fallback;
import io.micronaut.security.DefaultUserDetails;
import io.micronaut.security.UserDetails;
import io.micronaut.security.UsernamePassword;
import io.micronaut.security.jwt.AccessRefreshTokenGenerator;
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
            userDetails = new DefaultUserDetails("newton", Arrays.asList("ROLE_GROOVY"));
        } else if ( usernamePassword.getUsername().equals("euler") ) {
            userDetails = new DefaultUserDetails("euler", Arrays.asList("ROLE_GRAILS"));
        }
        return HttpResponse.ok().body(accessRefreshTokenGenerator.generate(userDetails));
    }
}
