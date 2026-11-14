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
import io.micronaut.core.convert.PrimitiveTargetTypeConverter;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.PrimitiveValueConverters;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormFieldException;
import io.micronaut.http.multipart.FormFieldMetadata;
import org.jspecify.annotations.Nullable;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CancellationException;
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
    private final Map<String, List<FormFieldMetadata>> fieldMetadata;
    private final ConversionService conversionService;
    /**
     * The form with the raw values of the fields, see {@link #raw()}, or {@code null} if unknown.
     */
    private volatile @Nullable FormData raw;
    // guarded by this
    private @Nullable CompletionStage<Void> closed;

    /**
     * @param fields            The values of the text fields
     * @param files             The uploaded files, with immutable lists
     * @param conversionService The conversion service of the route
     */
    DefaultFormData(Map<String, List<String>> fields, Map<String, List<FileUpload>> files, ConversionService conversionService) {
        this(fields, Map.of(), files, conversionService);
    }

    /**
     * @param fields            The values of the text fields
     * @param fieldMetadata     The metadata of the parts of the text fields, with immutable lists
     * @param files             The uploaded files, with immutable lists
     * @param conversionService The conversion service of the route
     */
    DefaultFormData(Map<String, List<String>> fields, Map<String, List<FormFieldMetadata>> fieldMetadata, Map<String, List<FileUpload>> files, ConversionService conversionService) {
        this.fields = fields;
        this.fieldMetadata = fieldMetadata;
        this.files = files;
        this.conversionService = conversionService;
    }

    /**
     * The form of the raw values of a URL-encoded form, as {@link io.micronaut.http.body.FormOptions#withRawValues()}
     * reads them, decoded, with the raw form as its {@link #raw()} view, see
     * {@link io.micronaut.http.body.FormOptions#withRawView()}.
     *
     * @param rawForm The form with the values as they appear in the body
     * @param charset The charset of the form
     * @return The decoded form
     * @throws FormFieldException if a value is not validly percent-encoded
     */
    static DefaultFormData decoded(DefaultFormData rawForm, Charset charset) {
        Map<String, List<String>> decoded = new LinkedHashMap<>();
        rawForm.fields.forEach((name, values) -> {
            List<String> list = new ArrayList<>(values.size());
            for (String value : values) {
                try {
                    list.add(URLDecoder.decode(value, charset));
                } catch (IllegalArgumentException e) {
                    throw new FormFieldException(name, "Form field [" + name + "] is not a valid URL-encoded value");
                }
            }
            decoded.put(name, list);
        });
        DefaultFormData form = new DefaultFormData(decoded, rawForm.fieldMetadata, rawForm.files, rawForm.conversionService);
        rawForm.raw = rawForm;
        form.raw = rawForm;
        return form;
    }

    /**
     * Mark the form as its own raw form: a form without percent-encoding, e.g. a
     * {@code multipart/form-data} form, or a form without fields.
     *
     * @param form A form
     * @return The form
     */
    static FormData rawIsDecoded(FormData form) {
        if (form instanceof DefaultFormData defaultForm) {
            defaultForm.raw = defaultForm;
        }
        return form;
    }

    @Override
    public FormData raw() {
        FormData known = raw;
        if (known == null) {
            throw new UnsupportedOperationException("The raw values of the form are not available: read the form with FormOptions.withRawView()");
        }
        return known;
    }

    @Override
    public List<FormFieldMetadata> getFieldMetadata(String name) {
        return fieldMetadata.getOrDefault(name, List.of());
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

    // The first value of a field converts to a primitive with the converter of the conversion service,
    // without boxing it; a value it does not convert and a missing field go through the conversion to
    // the wrapper type, which fails as it does for any type.

    @Override
    public int getInt(String name) {
        String value = first(name);
        if (value != null && value.length() <= PrimitiveValueConverters.MAX_PARSED_LENGTH) {
            PrimitiveTargetTypeConverter<String, Integer> converter = PrimitiveValueConverters.intConverter(conversionService);
            if (converter != null) {
                // a value that does not parse converts again below, with the error of the field
                OptionalInt parsed = converter.findInt(value, ConversionContext.DEFAULT);
                if (parsed.isPresent()) {
                    return parsed.getAsInt();
                }
            }
        }
        return FormData.super.getInt(name);
    }

    @Override
    public int getInt(String name, int defaultValue) {
        return first(name) != null ? getInt(name) : FormData.super.getInt(name, defaultValue);
    }

    @Override
    public OptionalInt findInt(String name) {
        return first(name) != null ? OptionalInt.of(getInt(name)) : FormData.super.findInt(name);
    }

    @Override
    public long getLong(String name) {
        String value = first(name);
        if (value != null && value.length() <= PrimitiveValueConverters.MAX_PARSED_LENGTH) {
            PrimitiveTargetTypeConverter<String, Long> converter = PrimitiveValueConverters.longConverter(conversionService);
            if (converter != null) {
                // a value that does not parse converts again below, with the error of the field
                OptionalLong parsed = converter.findLong(value, ConversionContext.DEFAULT);
                if (parsed.isPresent()) {
                    return parsed.getAsLong();
                }
            }
        }
        return FormData.super.getLong(name);
    }

    @Override
    public long getLong(String name, long defaultValue) {
        return first(name) != null ? getLong(name) : FormData.super.getLong(name, defaultValue);
    }

    @Override
    public OptionalLong findLong(String name) {
        return first(name) != null ? OptionalLong.of(getLong(name)) : FormData.super.findLong(name);
    }

    @Override
    public double getDouble(String name) {
        String value = first(name);
        if (value != null && value.length() <= PrimitiveValueConverters.MAX_PARSED_LENGTH) {
            PrimitiveTargetTypeConverter<String, Double> converter = PrimitiveValueConverters.doubleConverter(conversionService);
            if (converter != null) {
                // a value that does not parse converts again below, with the error of the field
                OptionalDouble parsed = converter.findDouble(value, ConversionContext.DEFAULT);
                if (parsed.isPresent()) {
                    return parsed.getAsDouble();
                }
            }
        }
        return FormData.super.getDouble(name);
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        return first(name) != null ? getDouble(name) : FormData.super.getDouble(name, defaultValue);
    }

    @Override
    public OptionalDouble findDouble(String name) {
        return first(name) != null ? OptionalDouble.of(getDouble(name)) : FormData.super.findDouble(name);
    }

    @Override
    public boolean getBoolean(String name) {
        String value = first(name);
        if (value != null) {
            PrimitiveTargetTypeConverter<String, Boolean> converter = PrimitiveValueConverters.booleanConverter(conversionService);
            if (converter != null) {
                try {
                    return converter.getBoolean(value, ConversionContext.DEFAULT);
                } catch (ConversionErrorException e) {
                    // converted again below, with the error of the field
                }
            }
        }
        return FormData.super.getBoolean(name);
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        return first(name) != null ? getBoolean(name) : FormData.super.getBoolean(name, defaultValue);
    }

    /**
     * The first value of a field, as {@link #get(String, Class)} converts it, or {@code null} if it has none.
     */
    private @Nullable String first(String name) {
        List<String> values = fields.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
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
    public <T> Optional<T> find(String name, Argument<T> type) {
        List<String> values = fields.get(name);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        Class<T> rawType = type.getType();
        // a collection or an array gets every value of the field, and an Optional of one
        boolean all = FormBinding.isMany(type);
        if (all && rawType == Optional.class) {
            // converted to the collection: the conversion to an Optional does not report the
            // values of the collection that were rejected
            Argument<?> valueType = type.getFirstTypeVariable().orElseThrow();
            Object value = convert(Argument.of(valueType.getType(), name, valueType.getAnnotationMetadata(), valueType.getTypeParameters()), List.copyOf(values));
            return Optional.of(rawType.cast(Optional.of(value)));
        }
        Argument<T> named = name.equals(type.getName()) ? type : Argument.of(rawType, name, type.getAnnotationMetadata(), type.getTypeParameters());
        return Optional.of(convert(named, all ? List.copyOf(values) : values.get(0)));
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
                // every stage is complete here: its outcome is read without waiting
                Throwable cause = switch (stage.state()) {
                    case FAILED -> stage.exceptionNow();
                    case CANCELLED -> new CancellationException("Releasing an upload was cancelled");
                    default -> null;
                };
                if (cause != null) {
                    if (failure == null) {
                        failure = cause;
                    } else if (failure != cause) {
                        failure.addSuppressed(cause);
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

    private <T> T convert(Argument<T> argument, Object value) {
        if (argument.getTypeParameters().length == 0 && argument.getType().isInstance(value)) {
            return argument.getType().cast(value);
        }
        ConversionContext context = ConversionContext.of(argument);
        Optional<T> result = conversionService.convert(value, argument.getType(), context);
        // checked first: a collection is converted from the values that convert, and the
        // others are rejected, which is not the value that was asked for
        Optional<ConversionError> error = context.getLastError();
        if (error.isPresent()) {
            throw new ConversionErrorException(argument, error.get());
        }
        if (result.isPresent()) {
            return result.get();
        }
        throw new FormFieldException(argument.getName(), "Form field [" + argument.getName() + "] cannot be converted to " + argument.getTypeName());
    }

    @Override
    public String toString() {
        return "FormData" + fields.keySet() + files.keySet();
    }
}
