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
package io.micronaut.docs.security.securityRule.permitall;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

@Requires(property = "spec.name", value = "docpermitall")
//tag::exampleControllerPlusImports[]
@Controller("/example")
public class ExampleController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/admin")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_X"}) // <1>
    public String withroles() {
        return "You have ROLE_ADMIN or ROLE_X roles";
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/anonymous")
    @PermitAll  // <2>
    public String anonymous() {
        return "You are anonymous";
    }
}
//end::exampleControllerPlusImports[]