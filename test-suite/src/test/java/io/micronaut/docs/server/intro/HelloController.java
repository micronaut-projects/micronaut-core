/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.intro;

import io.micronaut.context.annotation.Requires;

// tag::imports[]
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
// end::imports[]
@Requires(property = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@Controller("/hello") // <1>
public class HelloController {

    @Get(produces = MediaType.TEXT_PLAIN) // <2>
    public String index() {
        return "Hello World"; // <3>
    }
}
// end::class[]
