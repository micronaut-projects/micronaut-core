/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.security.securityRule.secured

import io.micronaut.context.annotation.Requires

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule

// Although, it is a Groovy Class is written as Java syntax as possible to embedded in the docs

@Requires(property = 'spec.name', value = 'docsecured')
//tag::exampleControllerPlusImports[]
@Controller("/example")
@Secured(SecurityRule.IS_AUTHENTICATED) // <1>
public class ExampleController {

    @Get("/admin")
    @Secured(["ROLE_ADMIN", "ROLE_X"]) // <2>
    public String withroles() {
        return "You have ROLE_ADMIN or ROLE_X roles";
    }

    @Get('/anonymous')
    @Secured(SecurityRule.IS_ANONYMOUS)  // <3>
    public String anonymous() {
        return "You are anonymous";
    }

    @Get("/authenticated") // <1>
    public String authenticated(Authentication authentication) {
        return "${authentication.name} is authenticated";
    }
}
//end::exampleControllerPlusImports[]