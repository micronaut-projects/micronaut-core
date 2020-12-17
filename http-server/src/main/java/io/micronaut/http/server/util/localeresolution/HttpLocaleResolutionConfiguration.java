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
package io.micronaut.http.server.util.localeresolution;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.localeresolution.LocaleResolutionConfiguration;

import java.util.Optional;

/**
 * Configuration for Locale Resolution in a HTTP Request.
 *
 * @author Sergio del Amo
 * @since 2.3.0
 */
public interface HttpLocaleResolutionConfiguration extends LocaleResolutionConfiguration {
    /**
     * @return The key in the session that stores the locale
     */
    @NonNull
    Optional<String> getSessionAttribute();

    /**
     * @return The name of the cookie that contains the locale.
     */
    @NonNull
    Optional<String> getCookieName();

    /**
     * @return True if the accept header should be searched for the locale.
     */
    boolean isHeader();
}
