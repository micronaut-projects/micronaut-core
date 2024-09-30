/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.NonNull;

/**
 * Utility methods for working with {@link MediaType}.
 * @author Sergio del Amo
 * @since 4.7.0
 */
public final class MediaTypeUtils {

    /**
     *
     * @param mediaType Media Type
     * @return Returns true if the media type is {@link MediaType#APPLICATION_JSON_TYPE}, {@link MediaType#TEXT_JSON_TYPE}, {@link MediaType#APPLICATION_HAL_JSON_TYPE}, {@link MediaType#APPLICATION_JSON_GITHUB_TYPE}, {@link MediaType#APPLICATION_JSON_FEED_TYPE}, {@link {@link MediaType#APPLICATION_JSON_PROBLEM_TYPE}, {@link MediaType#APPLICATION_JSON_PATCH_TYPE}, {@link MediaType#APPLICATION_JSON_MERGE_PATCH_TYPE} or {@link MediaType#APPLICATION_JSON_SCHEMA_TYPE}.
     */
    public static boolean isJson(@NonNull MediaType mediaType) {
        return mediaType.equals(MediaType.APPLICATION_JSON_TYPE)
            || mediaType.equals(MediaType.TEXT_JSON_TYPE)
            || mediaType.equals(MediaType.APPLICATION_HAL_JSON_TYPE)
            || mediaType.equals(MediaType.APPLICATION_JSON_GITHUB_TYPE)
            || mediaType.equals(MediaType.APPLICATION_JSON_FEED_TYPE)
            || mediaType.equals(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
            || mediaType.equals(MediaType.APPLICATION_JSON_PATCH_TYPE)
            || mediaType.equals(MediaType.APPLICATION_JSON_MERGE_PATCH_TYPE)
            || mediaType.equals(MediaType.APPLICATION_JSON_SCHEMA_TYPE);

    }
}
