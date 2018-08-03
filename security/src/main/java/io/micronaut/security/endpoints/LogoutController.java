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
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.event.LogoutEvent;
import io.micronaut.security.handlers.LogoutHandler;
import io.micronaut.security.rules.SecurityRule;
import javax.annotation.Nullable;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = LogoutControllerConfigurationProperties.PREFIX + ".enabled")
@Controller("${" + LogoutControllerConfigurationProperties.PREFIX + ".path:/logout}")
@Secured(SecurityRule.IS_ANONYMOUS)
public class LogoutController {

    protected final LogoutHandler logoutHandler;
    protected final ApplicationEventPublisher eventPublisher;

    /**
     *
     * @param logoutHandler A collaborator which helps to build HTTP response if user logout.
     * @param eventPublisher The application event publisher
     */
    public LogoutController(@Nullable LogoutHandler logoutHandler,
                            ApplicationEventPublisher eventPublisher) {
        this.logoutHandler = logoutHandler;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param request The {@link HttpRequest} being executed
     * @param authentication {@link Authentication} instance for current user
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON})
    @Post
    public HttpResponse index(HttpRequest<?> request, Authentication authentication) {
        eventPublisher.publishEvent(new LogoutEvent(authentication));
        if (logoutHandler != null) {
            return logoutHandler.logout(request);
        }
        return HttpResponse.notFound();
    }
}
