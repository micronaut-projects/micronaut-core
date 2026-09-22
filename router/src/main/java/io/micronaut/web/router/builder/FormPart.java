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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.StreamingFileUpload;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A part of a form read with {@link FormParts#forEach}: a text field, or a file of a
 * {@code multipart/form-data} form. Its content can be read once, as text, as a stream or into a
 * file; content that is not read is discarded when the consumer's stage completes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface FormPart {

    /**
     * @return The name of the field
     */
    String name();

    /**
     * @return The name of the uploaded file, or {@code null} for a text field
     */
    @Nullable String fileName();

    /**
     * @return Whether the part is an uploaded file
     */
    default boolean isFile() {
        return fileName() != null;
    }

    /**
     * @return The content type of the part, if given
     */
    Optional<MediaType> contentType();

    /**
     * Read the whole content as text, in the charset of the request.
     *
     * @return Completes with the content
     */
    CompletionStage<String> text();

    /**
     * Stream the content, e.g. to a file, an output stream or as a publisher of data.
     *
     * @return The content as a streaming upload
     */
    StreamingFileUpload stream();

    /**
     * Write the content to a file.
     *
     * @param file The file
     * @return Completes when the content is written
     */
    CompletionStage<Void> transferTo(Path file);
}
