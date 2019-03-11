/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.security.utils

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.annotation.Secured
import javax.annotation.Nullable

@Requires(env = Environment.TEST)
@Requires(property = SecurityServiceSpec.SPEC_NAME_PROPERTY, value = 'SecurityServiceSpec')
@Controller(SecurityServiceSpec.controllerPath)
class SecurityServiceController {

    private final SecurityService securityService

    SecurityServiceController(SecurityService securityService) {
        this.securityService = securityService
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/authenticated")
    boolean authenticated() {
        securityService.isAuthenticated()
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/currentuser")
    String currentuser() {
        Optional<String> str = securityService.username()
        str.map { m -> m}.orElse("Anonymous")
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/roles{?role}")
    Boolean roles(@Nullable String role) {
        securityService.hasRole(role)
    }
}
