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
import io.micronaut.http.form.FormFieldException;
import io.micronaut.http.form.FormPart;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A {@link FormPart} read from the form as it arrives. The part and its {@link #file()} view share
 * one {@link UploadContent}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormPart implements FormPart {
    private final UploadContent content;
    private final @Nullable FileUpload file;

    DefaultFormPart(UploadContent content) {
        this.content = content;
        this.file = content.fileName() != null ? new DefaultFileUpload(content) : null;
    }

    @Override
    public String name() {
        return content.name();
    }

    @Override
    public @Nullable String fileName() {
        return content.fileName();
    }

    @Override
    public Optional<MediaType> contentType() {
        return content.contentType();
    }

    @Override
    public FileUpload file() {
        FileUpload f = file;
        if (f == null) {
            throw FormFieldException.notAFile(name());
        }
        return f;
    }

    @Override
    public CompletionStage<String> text() {
        return content.text(content.context.maxBufferSize());
    }

    @Override
    public CompletionStage<String> text(int maximumBytes) {
        return content.text(maximumBytes);
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
    public CompletionStage<Void> transferTo(OutputStream out) {
        return content.transferTo(out);
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
        return "FormPart[" + content.metadata + "]";
    }
}
