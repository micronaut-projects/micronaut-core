package example.security.gateway;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.Client;
import io.micronaut.http.client.HttpClient;
import io.micronaut.security.jwt.TokenRefreshRequest;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Controller("/oauth")
public class OauthController {

    @Inject
    @Client(id = "security")
    HttpClient httpClient;

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/access_token")
    Publisher token(TokenRefreshRequest tokenRefreshRequest) {
        HttpRequest req = HttpRequest.create(HttpMethod.POST, "/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .body(tokenRefreshRequest);
        return httpClient.retrieve(req);
    }
}
