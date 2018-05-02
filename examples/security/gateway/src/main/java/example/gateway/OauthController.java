package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.endpoints.TokenRefreshRequest;
import io.micronaut.security.token.jwt.render.AccessRefreshToken;
import javax.inject.Singleton;

@Singleton
@Controller("/oauth")
public class OauthController {

    protected final OauthClient oauthClient;

    public OauthController(OauthClient oauthClient) {
        this.oauthClient = oauthClient;
    }

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/access_token")
    HttpResponse<AccessRefreshToken> token(TokenRefreshRequest tokenRefreshRequest) {
        return oauthClient.token(tokenRefreshRequest);
    }
}
