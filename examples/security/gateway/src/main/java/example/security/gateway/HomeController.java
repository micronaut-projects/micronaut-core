/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.security.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.UsernamePassword;

import javax.inject.Singleton;
import java.net.URI;
import static io.micronaut.http.HttpResponse.ok;

@Singleton
@Controller("/")
public class HomeController {

    protected final LoginClient loginClient;

    public HomeController(LoginClient loginClient) {
        this.loginClient = loginClient;
    }

    @Produces(MediaType.TEXT_HTML)
    @Get(uri = "/")
    HttpResponse index() {
        return HttpResponse.redirect(URI.create("/index.html"));
    }

    @Get("/notInInterceptUrlMap")
    HttpResponse notInInterceptUrlMap() {
        return ok();
    }

    @Post("/login")
    public HttpResponse login(@Body UsernamePassword usernamePassword) {
        HttpResponse rsp = loginClient.login(usernamePassword);
        return HttpResponse.status(rsp.status()).body(rsp.body());
    }

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/auth")
    public HttpResponse auth(String username, String password) {
        HttpResponse rsp = loginClient.login(new UsernamePassword(username, password));
        return HttpResponse.status(rsp.status()).body(rsp.body());
    }
}
