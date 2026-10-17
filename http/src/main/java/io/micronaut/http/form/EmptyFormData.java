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
package io.micronaut.http.form;

import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The form without fields, see {@link FormData#empty()}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class EmptyFormData implements FormData {
    static final EmptyFormData INSTANCE = new EmptyFormData();
    private static final CompletionStage<Void> CLOSED = CompletableFuture.completedFuture(null);

    private EmptyFormData() {
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public boolean contains(String name) {
        return false;
    }

    @Override
    public List<String> getValues(String name) {
        return List.of();
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        throw FormFieldException.missingField(name);
    }

    @Override
    public <T> Optional<T> find(String name, Class<T> type) {
        return Optional.empty();
    }

    @Override
    public List<FileUpload> getFiles(String name) {
        return List.of();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return CLOSED;
    }

    @Override
    public void close() {
        // nothing to release
    }

    @Override
    public String toString() {
        return "FormData[]";
    }
}
