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

package io.micronaut.security.authentication;

/**
 * Enums describes the different authentication failures.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public enum AuthenticationFailureReason {
    /**
     * Failure when the cause is the user is not found.
     */
    USER_NOT_FOUND,
    /**
     * Failure when the cause is the credentials don't match.
     */
    CREDENTIALS_DO_NOT_MATCH,
    /**
     * Failure when the cause is the user account is disabled.
     */
    USER_DISABLED,
    /**
     * Failure when the cause is the user account expired.
     */
    ACCOUNT_EXPIRED,
    /**
     * Failure when the cause is the user account was locked.
     */
    ACCOUNT_LOCKED,
    /**
     * Failure when the cause is the user's password expired.
     */
    PASSWORD_EXPIRED,
    /**
     * An unknown failure.
     */
    UNKNOWN
}
