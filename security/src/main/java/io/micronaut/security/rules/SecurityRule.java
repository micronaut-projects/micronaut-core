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

package io.micronaut.security.rules;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Informs the JWT filter what to do with the given request.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface SecurityRule extends Ordered {

    /**
     * The token to represent allowing anonymous access.
     */
    String IS_ANONYMOUS = "isAnonymous()";

    /**
     * The token to represent allowing any authenticated access.
     */
    String IS_AUTHENTICATED = "isAuthenticated()";

    /**
     * Returns a security result based on any conditions.
     * @see SecurityRuleResult
     *
     * @param request The current request
     * @param routeMatch The matched route or empty if no route was matched. e.g. static resource.
     * @param claims The claims from the token. Null if not authenticated
     * @return The result
     */
    SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable  Map<String, Object> claims);
}
