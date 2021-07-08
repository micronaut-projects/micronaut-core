/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.client.bind.binders;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpParameters;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;

import javax.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

/**
 * Implementation of the binder for the {@link QueryValue} annotation.
 *
 * @since 3.0.0
 * @author Andriy Dmytruk
 */

public class QueryValueClientArgumentBinder implements AnnotatedClientArgumentRequestBinder<QueryValue> {
    private static final String COMMA_DELIMITER = ",";
    private static final String PIPE_DELIMITER = encodeURIComponent("|");
    private static final String SPACE_DELIMITER = encodeURIComponent(" ");

    private final ConversionService<?> conversionService;

    public QueryValueClientArgumentBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @NonNull
    @Override
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    @Override
    public void bind(@NotNull ArgumentConversionContext<Object> context,
                     @NonNull ClientRequestUriContext uriContext,
                     @NonNull Object value,
                     @NonNull MutableHttpRequest<?> request
    ) {
        String unencodedKey = context.getAnnotationMetadata().stringValue(QueryValue.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElse(context.getArgument().getName());
        String key = encodeURIComponent(unencodedKey);

        QueryValue.Format format = context.getAnnotationMetadata()
                .enumValue(QueryValue.class, "format", QueryValue.Format.class)
                .orElse(QueryValue.Format.URI_TEMPLATE_FORMAT);
        MutableHttpParameters parameters = request.getParameters();

        if (format == QueryValue.Format.URI_TEMPLATE_FORMAT) {
            uriContext.getPathParameters().put(key, value);
            convertToString(context, value).ifPresent(v -> uriContext.getQueryParameters().put(key, v));
        } else if (format == QueryValue.Format.DEEP_OBJECT) {
            addDeepObjectParameters(context, value, key, parameters);
        } else if (format == QueryValue.Format.MULTI) {
            addMultiParameters(context, value, key, parameters);
        } else {
            String delimiter = "";
            switch (format) {
                case SSV:
                    delimiter = SPACE_DELIMITER;
                    break;
                case PIPES:
                    delimiter = PIPE_DELIMITER;
                    break;
                case CSV:
                    delimiter = COMMA_DELIMITER;
                    break;
                default:
            }
            createSeparatedQueryValue(context, value, delimiter)
                    .ifPresent(v -> parameters.add(key, v));
        }
    }

    private void addMultiParameters(
            ArgumentConversionContext<Object> context, Object value, String key, MutableHttpParameters parameters
    ) {
        if (value instanceof Iterable) {
            // noinspection unechecked
            Iterable<Object> iterable = (Iterable<Object>) value;

            for (Object item : iterable) {
                convertToEncodedURIComponent(context, item).ifPresent(v -> parameters.add(key, v));
            }
        } else {
            convertToEncodedURIComponent(context, value).ifPresent(v -> parameters.add(key, v));
        }
    }

    private void addDeepObjectParameters(
            ArgumentConversionContext<Object> context, Object value, String key, MutableHttpParameters parameters
    ) {
        if (value instanceof Iterable) {
            StringBuilder builder = new StringBuilder(key);

            // noinspection unechecked
            Iterable<Object> iterable = (Iterable<Object>) value;

            int i = 0;
            for (Object item: iterable) {
                if (item == null) {
                    continue;
                }
                String index = String.valueOf(i);

                builder.append('[');
                builder.append(index);
                builder.append(']');

                convertToEncodedURIComponent(context, item).ifPresent(v -> parameters.add(builder.toString(), v));
                builder.delete(builder.length() - index.length() - 2, builder.length());
                i++;
            }
        } else if (value != null) {
            StringBuilder builder = new StringBuilder(key);
            // noinspection unechecked
            BeanWrapper wrapper = BeanWrapper.getWrapper(value);
            // noinspection unchecked
            Collection<BeanProperty<Object, Object>> properties = wrapper.getBeanProperties();
            for (BeanProperty<Object, Object> property: properties) {
                Object item = property.get(value);
                if (item == null) {
                    continue;
                }
                builder.append('[');
                builder.append(property.getName());
                builder.append(']');

                convertToEncodedURIComponent(context, item).ifPresent(v -> parameters.add(builder.toString(), v));
                builder.delete(builder.length() - property.getName().length() - 2, builder.length());
            }
        }
    }

    private Optional<String> createSeparatedQueryValue(
            ArgumentConversionContext<Object> context, Object value, String delimiter
    ) {
        if (value instanceof Iterable) {
            StringBuilder builder = new StringBuilder();
            // noinspection unechecked
            Iterable<Object> iterable = (Iterable<Object>) value;

            boolean first = true;
            for (Object item : iterable) {
                Optional<String> opt = convertToEncodedURIComponent(context, item);
                if (opt.isPresent()) {
                    if (!first) {
                        builder.append(delimiter);
                    }
                    first = false;
                    builder.append(opt.get());
                }
            }

            return Optional.of(builder.toString());
        } else {
            return convertToEncodedURIComponent(context, value);
        }
    }

    private Optional<String> convertToString(ArgumentConversionContext<Object> context, Object value) {
        return conversionService.convert(value, ConversionContext.STRING.with(context.getAnnotationMetadata()))
                .filter(StringUtils::isNotEmpty);
    }

    private Optional<String> convertToEncodedURIComponent(ArgumentConversionContext<Object> context, Object value) {
        return convertToString(context, value)
                .map(QueryValueClientArgumentBinder::encodeURIComponent);
    }

    private static String encodeURIComponent(String component) {
        try {
            return URLEncoder.encode(component, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ignored) {
            return null;
        }
    }
}
