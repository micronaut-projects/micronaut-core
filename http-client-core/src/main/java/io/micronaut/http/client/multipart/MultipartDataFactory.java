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
package io.micronaut.http.client.multipart;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.http.MediaType;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * A factory for Multipart data.
 *
 * @author graemerocher
 * @since 2.0
 * @param <T> The file upload type
 */
public interface MultipartDataFactory<T> {

    /**
     * Creates a file upload.
     * @param name The name of the file
     * @param filename The file name
     * @param contentType The content type
     * @param encoding The encoding
     * @param charset The charset
     * @param length The length
     * @return The file upload
     */
    @NonNull T createFileUpload(
            @NonNull String name,
            @NonNull String filename,
            @NonNull MediaType contentType,
            @Nullable String encoding,
            @Nullable Charset charset,
            long length);

    /**
     * Creates an attribute.
     * @param name The name of the attribute
     * @param value The value of the attribute
     * @return The attribute
     */
    @NonNull T createAttribute(@NonNull String name, @NonNull String value);

    /**
     * Sets the content on the file upload object.
     * @param fileUploadObject The file upload object
     * @param content The content
     * @throws IOException When the content cannot be set
     */
    void setContent(T fileUploadObject, Object content) throws IOException;
}
