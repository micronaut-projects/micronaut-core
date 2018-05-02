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

package io.micronaut.security.endpoints;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.Secured;
import io.micronaut.security.handlers.LogoutHandler;
import io.micronaut.security.rules.SecurityRule;
import javax.annotation.Nullable;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Controller("/")
@Requires(property = SecurityEndpointsConfigurationProperties.PREFIX + ".logout")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class LogoutController {

    protected final LogoutHandler logoutHandler;

    /**
     * @param logoutHandler A collaborator which helps to build HTTP response if user logout.
     */
    public LogoutController(@Nullable LogoutHandler logoutHandler) {
        this.logoutHandler = logoutHandler;
    }

    /**
     * @param request The {@link HttpRequest} being executed
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON})
    @Post
    public HttpResponse logout(HttpRequest<?> request) {
         if (logoutHandler != null) {
             return logoutHandler.logout(request);
         }
         return HttpResponse.notFound();

    }
}
