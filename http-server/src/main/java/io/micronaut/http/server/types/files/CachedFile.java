/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.types.files;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;

/**
 * A static resource served from the in-memory cache.
 *
 * @param content      The content, never modified
 * @param mediaType    The media type
 * @param lastModified The last-modified time
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("java:S6218") // compared by identity
public record CachedFile(byte[] content, MediaType mediaType, long lastModified) implements FileCustomizableResponseType {

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getLength() {
        return content.length;
    }

    @Override
    public MediaType getMediaType() {
        return mediaType;
    }
}
