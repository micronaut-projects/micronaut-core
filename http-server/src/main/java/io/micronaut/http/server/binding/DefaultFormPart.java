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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.web.router.builder.FormPart;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A {@link FormPart} over a raw form field, whose content is read at most once.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormPart implements FormPart {
    private final RawFormField field;
    private final FormFactory formFactory;
    private final Charset charset;
    private volatile boolean read;

    DefaultFormPart(RawFormField field, FormFactory formFactory, Charset charset) {
        this.field = field;
        this.formFactory = formFactory;
        this.charset = charset;
    }

    @Override
    public String name() {
        return Objects.requireNonNullElse(field.metadata().name(), "");
    }

    @Override
    public @Nullable String fileName() {
        return field.metadata().fileName();
    }

    @Override
    public Optional<MediaType> contentType() {
        return Optional.ofNullable(field.metadata().mediaType());
    }

    @Override
    public CompletionStage<String> text() {
        claim();
        return InternalByteBody.bufferFlow(field.byteBody()).map(body -> {
            try (CloseableAvailableByteBody available = body) {
                return available.toString(charset);
            }
        }).toCompletableFuture();
    }

    @Override
    public StreamingFileUpload stream() {
        claim();
        return formFactory.streamFileUpload(field);
    }

    @Override
    public CompletionStage<Void> transferTo(Path file) {
        return Mono.from(stream().transferTo(file)).then().toFuture();
    }

    /**
     * Close the content if the consumer did not read it.
     */
    void discardIfUnread() {
        if (!read) {
            read = true;
            field.close();
        }
    }

    private void claim() {
        if (read) {
            throw new IllegalStateException("The content of form part " + name() + " was already read or discarded");
        }
        read = true;
    }

    @Override
    public String toString() {
        return "FormPart[" + field.metadata() + "]";
    }
}
