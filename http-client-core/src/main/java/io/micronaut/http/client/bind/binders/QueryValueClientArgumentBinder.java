package io.micronaut.http.client.bind.binders;

import io.micronaut.core.annotation.AnnotationValue;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

/**
 * Implementation of the binder for the {@link QueryValue} annotation
 *
 * @author Andriy Dmytruk
 */

@Singleton
public class QueryValueClientArgumentBinder implements AnnotatedClientArgumentRequestBinder<QueryValue> {
    private static final String COMMA_DELIMITER = ",";
    private static final String PIPE_DELIMITER = encodeURIComponent("|");
    private static final String SPACE_DELIMITER = encodeURIComponent(" ");

    @Inject
    private ConversionService<?> conversionService;

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
        AnnotationValue<QueryValue> annotation = context.getAnnotationMetadata()
                .getAnnotationValuesByType(QueryValue.class).get(0);
        String unencodedKey = context.getAnnotationMetadata().stringValue(QueryValue.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElse(context.getArgument().getName());
        String key = encodeURIComponent(unencodedKey);

        QueryValue.Format format = annotation.get("format", QueryValue.Format.class)
                .orElse(QueryValue.Format.COMMA_DELIMITED);
        MutableHttpParameters parameters = request.getParameters();

        if (format == QueryValue.Format.DEEP_OBJECT) {
            addDeepObjectParameters(context, value, key, parameters);
        } else if (format == QueryValue.Format.MULTI) {
            addMultiParameters(context, value, key, parameters);
        } else {
            String delimiter = "";
            switch(format) {
                case SPACE_DELIMITED:
                    delimiter = SPACE_DELIMITER;
                    break;
                case PIPE_DELIMITED:
                    delimiter = PIPE_DELIMITER;
                    break;
                case COMMA_DELIMITED:
                    delimiter = COMMA_DELIMITER;
                    break;
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
                valueToString(context, item).ifPresent(v -> parameters.add(key, v));
            }
        } else {
            valueToString(context, value).ifPresent(v -> parameters.add(key, v));
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

                valueToString(context, item).ifPresent(v -> parameters.add(builder.toString(), v));
                builder.delete(builder.length() - index.length() - 2, builder.length());
                i++;
            }
        } else if (value != null) {
            StringBuilder builder = new StringBuilder(key);
            // noinspection unechecked
            BeanWrapper wrapper = BeanWrapper.getWrapper(value);
            // noinspection unchecked
            Collection<BeanProperty<Object,Object>> properties = wrapper.getBeanProperties();
            for (BeanProperty<Object, Object> property: properties) {
                Object item = property.get(value);
                if (item == null) {
                    continue;
                }
                builder.append('[');
                builder.append(property.getName());
                builder.append(']');

                valueToString(context, item).ifPresent(v -> parameters.add(builder.toString(), v));
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
                Optional<String> opt = valueToString(context, item);
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
            return valueToString(context, value);
        }
    }

    private Optional<String> valueToString(ArgumentConversionContext<Object> context, Object value) {
        return conversionService.convert(value, ConversionContext.STRING.with(context.getAnnotationMetadata()))
                .filter(StringUtils::isNotEmpty)
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
