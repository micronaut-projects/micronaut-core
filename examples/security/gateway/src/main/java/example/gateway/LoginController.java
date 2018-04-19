package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;

@Controller("/")
public class LoginController {
    protected final LoginClient loginClient;

    public LoginController(LoginClient loginClient) {
        this.loginClient = loginClient;
    }

    @Post("/login")
    public HttpResponse<BearerAccessRefreshToken> login(@Body UsernamePasswordCredentials usernamePassword) {
        return loginClient.login(usernamePassword);
    }

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/auth")
    public HttpResponse auth(String username, String password) {
        return loginClient.login(new UsernamePasswordCredentials(username, password));
    }
}
