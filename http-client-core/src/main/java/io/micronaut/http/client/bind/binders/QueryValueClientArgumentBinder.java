package io.micronaut.http.client.bind.binders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpParameters;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import javax.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

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
    @Named("json")
    private ObjectMapper objectMapper;

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
        String key = context.getAnnotationMetadata().stringValue(QueryValue.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElse(context.getArgument().getName());
        key = encodeURIComponent(key);

        // Shortcut for String values
        if (value instanceof String) {
            request.getParameters().add(key, String.valueOf(value));
        }

        QueryValue.Format format = annotation.get("format", QueryValue.Format.class)
                .orElse(QueryValue.Format.COMMA_DELIMITED);

        JsonNode node = objectMapper.valueToTree(value);
        MutableHttpParameters parameters = request.getParameters();

        if (format == QueryValue.Format.DEEP_OBJECT) {
            addDeepObjectParameters(
                    node,
                    new StringBuilder(key),
                    parameters
            );
        } else if (format == QueryValue.Format.MULTI) {
            addMultiParameters(
                    node,
                    key,
                    parameters
            );
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
            request.getParameters().add(key, createSeparatedQueryValue(node, delimiter));
        }
    }

    private static void addMultiParameters(JsonNode node, String key, MutableHttpParameters parameters) {
        if (node.isValueNode()) {
            if (!node.isNull()) {
                parameters.add(key, encodeURIComponent(node.textValue()));
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); ++i) {
                if (!node.get(i).isValueNode()) {
                    continue;
                }
                parameters.add(key, encodeURIComponent(node.get(i).textValue()));
            }
        } else if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); it.next()) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (!entry.getValue().isValueNode()) {
                    continue;
                }
                parameters.add(
                        encodeURIComponent(entry.getKey()),
                        encodeURIComponent(entry.getValue().asText()));
            }
        }
    }

    private static void addDeepObjectParameters(JsonNode node, StringBuilder path, MutableHttpParameters parameters) {
        if (node.isValueNode()) {
            if (!node.isNull()) {
                parameters.add(path.toString(), node.asText());
            }
        } else if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> entry = it.next();

                // Add to path
                path.append("[");
                path.append(entry.getKey());
                path.append("]");

                // recurse
                addDeepObjectParameters(entry.getValue(), path, parameters);

                // Remove path parts
                path.delete(path.length() - entry.getKey().length() - 2, path.length());
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); ++i) {
                String key = String.valueOf(i);

                // Add to path
                path.append("[");
                path.append(key);
                path.append("]");

                // recurse
                addDeepObjectParameters(node.get(i), path, parameters);

                // Remove path parts
                path.delete(path.length() - key.length() - 2, path.length());
            }
        }
    }

    private static BeanIntrospection<Object> getIntrospection(ArgumentConversionContext<Object> context, Object object){
        Class<Object> argumentType = context.getArgument().getType();
        // noinspection unchecked
        return BeanIntrospector.SHARED.findIntrospection((Class<Object>) object.getClass())
                .orElseGet(() -> BeanIntrospector.SHARED.findIntrospection(argumentType).orElse(null));
    }

    private static String createSeparatedQueryValue(
            JsonNode node,
            String delimiter
    ) {
        if (node.isValueNode()) {
            return encodeURIComponent(node.asText());
        } else if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            if (node.size() > 0) {
                builder.append(encodeURIComponent(node.get(0).asText()));
            }
            for (int i = 1; i < node.size(); ++i) {
                if (node.isNull()) {
                    continue;
                }
                builder.append(delimiter);
                builder.append(encodeURIComponent(node.get(i).asText()));
            }

            return builder.toString();
        } else if (node.isObject()) {
            StringBuilder builder = new StringBuilder();

            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (entry.getValue().isNull()) {
                    continue;
                }
                builder.append(encodeURIComponent(entry.getKey()));
                builder.append(delimiter);
                builder.append(encodeURIComponent(entry.getValue().asText()));
                if (it.hasNext()) {
                    builder.append(delimiter);
                }
            }

            return builder.toString();
        }

        return null;
    }

    private static String encodeURIComponent(String component) {
        try {
            return URLEncoder.encode(component, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ignored) {
            return null;
        }
    }
}
