/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.session;

/**
 * Settings for session configuration.
 */
public interface SessionSettings {

    /**
     * The prefix to use for all session configuration.
     */
    String PREFIX = "micronaut.session";

    /**
     * The property name for HTTP session configuration.
     */
    String HTTP = PREFIX + ".http";

    /**
     * The property name for HTTP session cookie configuration.
     */
    String HTTP_COOKIE_STRATEGY = HTTP + ".cookie";

    /**
     * The property name for HTTP session header configuration.
     */
    String HTTP_HEADER_STRATEGY = HTTP + ".header";
}
