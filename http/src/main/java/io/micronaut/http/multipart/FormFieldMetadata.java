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
package io.micronaut.http.multipart;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.stream.NoTrailers;
import org.jspecify.annotations.Nullable;

/**
 * Form field metadata provided by a client. This is untrusted data! Each attribute is optional.
 *
 * @param name      The field name
 * @param fileName  The file upload file name
 * @param mediaType The content type of the part, of a file upload as well as of a text field
 * @param headers   All the headers of the part, as sent by the client, or empty headers when they
 *                  are not known (since 5.3.0)
 * @since 5.0.0
 * @author Jonas Konrad
 */
public record FormFieldMetadata(
    @Nullable String name,
    @Nullable String fileName,
    @Nullable MediaType mediaType,
    HttpHeaders headers
) {
    public static final FormFieldMetadata EMPTY = new FormFieldMetadata(null, null, null);

    /**
     * Metadata without the headers of the part.
     *
     * @param name      The field name
     * @param fileName  The file upload file name
     * @param mediaType The content type of the part
     */
    public FormFieldMetadata(@Nullable String name, @Nullable String fileName, @Nullable MediaType mediaType) {
        this(name, fileName, mediaType, NoTrailers.HEADERS);
    }
}
