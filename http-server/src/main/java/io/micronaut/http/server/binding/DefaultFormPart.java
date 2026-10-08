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
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormFieldException;
import io.micronaut.http.form.FormPart;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * A {@link FormPart} read from the form as it arrives. The part and its {@link #file()} view share
 * one {@link UploadContent}.
 *
 * <p>As the argument of a controller or a request filter method, the part is released like an
 * {@link io.micronaut.http.body.AsyncRequestBody}: a read of its content that is still running
 * when the method completed is aborted, see {@link #releaseBody()}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormPart extends UploadContentView implements FormPart, ReleasableRequestBody {
    private final @Nullable FileUpload file;
    private final @Nullable ReleasableRequestBody owner;

    DefaultFormPart(UploadContent content) {
        this(content, null);
    }

    /**
     * @param content The content of the part
     * @param owner   What is released with the part, e.g. the parts a request filter streamed it
     *                from, see {@link #releaseBody()}
     */
    DefaultFormPart(UploadContent content, @Nullable ReleasableRequestBody owner) {
        super(content);
        this.file = content.fileName() != null ? new DefaultFileUpload(content) : null;
        this.owner = owner;
    }

    @Override
    public @Nullable String fileName() {
        return content.fileName();
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

    /**
     * Abort the read of the content that is still running, and release what it staged. Content
     * that was not read is left to the request, which releases it when it ends, unless the part
     * has an owner, which is released as well: the parts of a request filter that streamed the
     * part, with the rest of the form.
     *
     * @return Completes when released
     */
    @Override
    public CompletionStage<Void> releaseBody() {
        ReleasableRequestBody o = owner;
        return o == null ? content.releaseRunning() : ReleasableRequestBody.both(content::releaseRunning, o).releaseBody();
    }

    @Override
    public String toString() {
        return "FormPart[" + content.metadata + "]";
    }
}
