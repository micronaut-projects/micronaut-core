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
package org.particleframework.docs.server.request;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.Controller;
import org.particleframework.web.router.annotation.Get;

import javax.inject.Singleton;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller("/request")
@Singleton
public class MessageController {

    @Get("/hello") // <2>
    HttpResponse<String> hello(HttpRequest<?> request) {
        String name = request.getParameters()
                             .getFirst("name")
                             .orElse("Nobody"); // <3>

        return HttpResponse.ok("Hello " + name + "!!")
                           .header("X-My-Header", "Foo"); // <4>
    }
}
