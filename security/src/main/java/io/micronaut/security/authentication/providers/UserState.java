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
package io.micronaut.security.authentication.providers;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface UserState {

    /**
     *
     * @return a string representing the user e.g. admin
     */
    String getUsername();

    /**
     *
     * @return typically encrypted string save in a persistence mechanism
     */
    String getPassword();

    /**
     *
     * @return true or false indicating whether the user is enabled or not. For example, enabled if they have confirmed their email address.
     */
    boolean isEnabled();

    /**
     *
     * @return true or false indicating whether the user's account is expired
     */
    boolean isAccountExpired();

    /**
     *
     * @return true or false indicating whether the user's account is locked
     */
    boolean isAccountLocked();

    /**
     *
     * @return true or false indicating whether the user's password has expired
     */
    boolean isPasswordExpired();
}
