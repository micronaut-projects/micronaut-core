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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormFieldException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link FormData} of a request: the text fields, copied, and the uploaded files, owned by
 * the form until they are consumed.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormData implements FormData {
    private final Map<String, List<String>> fields;
    private final Map<String, List<FileUpload>> files;
    private final ConversionService conversionService;
    // guarded by this
    private @Nullable CompletionStage<Void> closed;

    /**
     * @param fields            The values of the text fields
     * @param files             The uploaded files, with immutable lists
     * @param conversionService The conversion service of the route
     */
    DefaultFormData(Map<String, List<String>> fields, Map<String, List<FileUpload>> files, ConversionService conversionService) {
        this.fields = fields;
        this.files = files;
        this.conversionService = conversionService;
    }

    @Override
    public Set<String> names() {
        return Collections.unmodifiableSet(fields.keySet());
    }

    @Override
    public boolean contains(String name) {
        return fields.containsKey(name);
    }

    @Override
    public List<String> getValues(String name) {
        List<String> values = fields.get(name);
        return values == null ? List.of() : Collections.unmodifiableList(values);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        List<String> values = fields.get(name);
        if (values == null || values.isEmpty()) {
            throw FormFieldException.missingField(name);
        }
        return convert(Argument.of(type, name), values.get(0));
    }

    @Override
    public <T> Optional<T> find(String name, Class<T> type) {
        List<String> values = fields.get(name);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(convert(Argument.of(type, name), values.get(0)));
    }

    @Override
    public List<FileUpload> getFiles(String name) {
        return files.getOrDefault(name, List.of());
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (this) {
            if (closed != null) {
                return closed;
            }
        }
        CompletionStage<Void> result = closeAll(files.values());
        synchronized (this) {
            if (closed == null) {
                closed = result;
            }
            return closed;
        }
    }

    @Override
    public void close() {
        closeAsync();
    }

    /**
     * Close uploads, completing when all of them are released, exceptionally with the first
     * failure and the others suppressed.
     *
     * @param uploads The uploads
     * @return Completes when released
     */
    static CompletionStage<Void> closeAll(Iterable<? extends List<FileUpload>> uploads) {
        List<CompletableFuture<Void>> stages = new ArrayList<>();
        for (List<FileUpload> list : uploads) {
            for (FileUpload upload : list) {
                stages.add(upload.closeAsync().toCompletableFuture());
            }
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.allOf(stages.toArray(new CompletableFuture[0])).whenComplete((ignored, error) -> {
            Throwable failure = null;
            for (CompletableFuture<Void> stage : stages) {
                if (stage.isCompletedExceptionally()) {
                    try {
                        stage.join();
                    } catch (Throwable e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (failure == null) {
                            failure = cause;
                        } else if (failure != cause) {
                            failure.addSuppressed(cause);
                        }
                    }
                }
            }
            if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                result.complete(null);
            }
        });
        return result.minimalCompletionStage();
    }

    private <T> T convert(Argument<T> argument, String value) {
        if (argument.getType().isInstance(value)) {
            return argument.getType().cast(value);
        }
        ConversionContext context = ConversionContext.of(argument);
        Optional<T> result = conversionService.convert(value, argument.getType(), context);
        if (result.isPresent()) {
            return result.get();
        }
        Optional<ConversionError> error = context.getLastError();
        if (error.isPresent()) {
            throw new ConversionErrorException(argument, error.get());
        }
        throw new FormFieldException(argument.getName(), "Form field [" + argument.getName() + "] cannot be converted to " + argument.getTypeName());
    }

    @Override
    public String toString() {
        return "FormData" + fields.keySet() + files.keySet();
    }
}
