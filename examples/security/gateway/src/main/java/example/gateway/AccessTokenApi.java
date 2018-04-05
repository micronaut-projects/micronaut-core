package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.controllers.TokenRefreshRequest;

public interface AccessTokenApi {

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/oauth/access_token")
    HttpResponse token(TokenRefreshRequest tokenRefreshRequest);
}
