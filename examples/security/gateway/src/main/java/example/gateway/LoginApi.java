package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.controllers.UsernamePassword;

public interface LoginApi {
    @Post
    HttpResponse login(@Body UsernamePassword usernamePassword);
}
