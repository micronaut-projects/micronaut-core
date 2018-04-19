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

package io.micronaut.security.filters;

import io.micronaut.http.HttpMethod;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.endpoints.LoginController;
import io.micronaut.security.endpoints.OauthController;
import io.micronaut.security.endpoints.SecurityEndpointsConfiguration;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class SecurityControllersEndpointAccessProvider implements EndpointAccessProvider {

    protected final SecurityEndpointsConfiguration securityEndpointsConfiguration;

    /**
     *
     * @param securityEndpointsConfiguration Configuration which controls Security controllers visibility
     */
    public SecurityControllersEndpointAccessProvider(SecurityEndpointsConfiguration securityEndpointsConfiguration) {
        this.securityEndpointsConfiguration = securityEndpointsConfiguration;
    }

    /**
     *
     * @return a List of {@link InterceptUrlMapPattern}
     */
    @Override
    public Optional<List<InterceptUrlMapPattern>> findEndpointAccessRestrictions() {
        final List<InterceptUrlMapPattern> results = new ArrayList<>();
        final List<String> access = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY);
        if (securityEndpointsConfiguration != null) {
            if (securityEndpointsConfiguration.isLogin()) {
                results.add(new InterceptUrlMapPattern(LoginController.LOGIN_PATH, access, HttpMethod.POST));
            }

            if (securityEndpointsConfiguration.isRefresh()) {
                final StringBuilder sb = new StringBuilder();
                sb.append(OauthController.CONTROLLER_PATH);
                sb.append(OauthController.ACCESS_TOKEN_PATH);
                final String pattern = sb.toString();
                results.add(new InterceptUrlMapPattern(pattern, access, HttpMethod.POST));
            }
        }
        return Optional.of(results);
    }
}
