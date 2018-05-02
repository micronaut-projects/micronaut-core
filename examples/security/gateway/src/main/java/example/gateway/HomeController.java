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
package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.authentication.UsernamePasswordCredentials;

import java.net.URI;
import static io.micronaut.http.HttpResponse.ok;

@Controller("/")
public class HomeController {

    @Produces(MediaType.TEXT_HTML)
    @Get(uri = "/")
    HttpResponse index() {
        return HttpResponse.temporaryRedirect(URI.create("/index.html"));
    }

    @Get("/notInInterceptUrlMap")
    HttpResponse notInInterceptUrlMap() {
        return ok();
    }
}
