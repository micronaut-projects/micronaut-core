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
import io.micronaut.http.form.FileUpload;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * A {@link FileUpload}: a view of an {@link UploadContent}, which holds its state.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFileUpload implements FileUpload {
    private final UploadContent content;

    DefaultFileUpload(UploadContent content) {
        this.content = content;
    }

    @Override
    public String name() {
        return content.name();
    }

    @Override
    public String fileName() {
        return Objects.requireNonNullElse(content.fileName(), "");
    }

    @Override
    public Optional<MediaType> contentType() {
        return content.contentType();
    }

    @Override
    public OptionalLong size() {
        return content.size();
    }

    @Override
    public OptionalLong expectedSize() {
        return content.expectedSize();
    }

    @Override
    public CompletionStage<byte[]> bytes(int maximumBytes) {
        return content.bytes(maximumBytes);
    }

    @Override
    public CompletionStage<Void> transferTo(Path destination) {
        return content.transferTo(destination);
    }

    @Override
    public CloseableByteBody takeBody() {
        return content.takeBody();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return content.closeAsync();
    }

    @Override
    public void close() {
        content.closeAsync();
    }

    @Override
    public String toString() {
        return "FileUpload[" + content.metadata + "]";
    }
}
