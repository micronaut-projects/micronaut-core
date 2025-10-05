/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.web.router;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.http.uri.URLEncodingKind;

/**
 * Configuration for the Router.
 *
 * @param urlDecoding The URL encoding specification to use.
 */
@ConfigurationProperties(RouterConfiguration.PREFIX)
public record RouterConfiguration(
    @NextMajorVersion("Change default to RFC_3986")
    @NonNull
    @Bindable(defaultValue = "RFC_1866")
    URLEncodingKind urlDecoding
) {

    public static final String PREFIX = "micronaut.router";

    @NextMajorVersion("Change default to RFC_3986")
    public RouterConfiguration {
        if (urlDecoding == null) {
            urlDecoding = URLEncodingKind.RFC_1866;
        }
    }
}
