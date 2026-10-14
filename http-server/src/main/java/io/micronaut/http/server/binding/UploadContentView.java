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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.CloseableByteBody;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * The operations a {@link DefaultFileUpload} and a {@link DefaultFormPart} share: both are views
 * of an {@link UploadContent}, which holds their state.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
abstract sealed class UploadContentView permits DefaultFileUpload, DefaultFormPart {
    final UploadContent content;

    UploadContentView(UploadContent content) {
        this.content = content;
    }

    /**
     * @return The name of the field
     */
    public final String name() {
        return content.name();
    }

    /**
     * @return The content type of the field, if it has one
     */
    public final Optional<MediaType> contentType() {
        return content.contentType();
    }

    /**
     * @param maximumBytes The maximum number of bytes
     * @return The bytes of the field
     */
    public final CompletionStage<byte[]> bytes(int maximumBytes) {
        return content.bytes(maximumBytes);
    }

    /**
     * @param destination The file to write
     * @return Completes when the content was written
     */
    public final CompletionStage<Void> transferTo(Path destination) {
        return content.transferTo(destination);
    }

    /**
     * @param out The stream to write to
     * @return Completes when the content was written
     */
    public final CompletionStage<Void> transferTo(OutputStream out) {
        return content.transferTo(out);
    }

    /**
     * @param maximumBytes The maximum number of bytes
     * @return The text of the field
     */
    public final CompletionStage<String> text(int maximumBytes) {
        return content.text(maximumBytes);
    }

    /**
     * @param maximumBytes The maximum number of bytes
     * @param charset      The charset
     * @return The text of the field
     */
    public final CompletionStage<String> text(int maximumBytes, Charset charset) {
        return content.text(maximumBytes, charset);
    }

    /**
     * @return The body of the field
     */
    public final CloseableByteBody takeBody() {
        return content.takeBody();
    }

    /**
     * @return Completes when the content was released
     */
    public final CompletionStage<Void> closeAsync() {
        return content.closeAsync();
    }

    /**
     * Start releasing the content.
     */
    public final void close() {
        content.closeAsync();
    }
}
