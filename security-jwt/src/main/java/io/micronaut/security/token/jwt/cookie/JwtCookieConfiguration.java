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

package io.micronaut.security.token.jwt.cookie;

import io.micronaut.core.util.Toggleable;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface JwtCookieConfiguration extends Toggleable {

    /**
     * cookieName getter.
     * @return a String with the Cookie Name
     */
    String getCookieName();

    /**
     *
     * @return String to be parsed into a URI which represents where the user is redirected to after a successful login.
     */
    String getLoginSuccessTargetUrl();

    /**
     *
     * @return String to be parsed into a URI which represents where the user is redirected to after logout.
     */
    String getLogoutTargetUrl();

    /**
     *
     * @return String to be parsed into a URI which represents where the user is redirected to after a failed login.
     */
    String getLoginFailureTargetUrl();
}
