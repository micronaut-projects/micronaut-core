/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of expanding for <a href="https://tools.ietf.org/html/rfc6570">rfc6570</a>.
 *
 * @author Denis Stepanov
 * @since 4.6.0
 */
@Internal
final class UriTemplateExpander implements UriTemplateParser.PartVisitor {

    private final Map<String, Object> parameters;
    private final StringBuilder builder = new StringBuilder();
    private boolean queryStarted;
    private boolean hashStarted;
    private boolean needsSeparator;

    UriTemplateExpander(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public void visitLiteral(String literal) {
        builder.append(literal);
    }

    @Override
    public void visitExpression(UriTemplateParser.ExpressionType type, List<UriTemplateParser.Variable> variables) {
        for (UriTemplateParser.Variable variable : variables) {
            appendValue(type, variable);
        }
        needsSeparator = false;
    }

    @Override
    public String toString() {
        return builder.toString();
    }

    private void appendValue(UriTemplateParser.ExpressionType type, UriTemplateParser.Variable variable) {
        Object value = parameters.get(variable.name());
        value = value instanceof Optional<?> optional ? optional.orElse(null) : value;
        if (value == null) {
            return;
        }
        if (value.getClass().isArray()) {
            value = Arrays.asList((Object[]) value);
        }
        if (variable.explode()) {
            value = expandPOJO(value);
        }

        if (value instanceof Iterable<?> iterable) {
            List<String> values = asListOfString(iterable);
            if (!values.isEmpty()) {
                appendListValues(type, variable, values);
            }

        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return;
            }
            List<Map.Entry<String, List<String>>> mapOfStrings = map.entrySet()
                .stream()
                .filter(e -> e.getValue() != null)
                .map(e -> Map.entry(e.getKey().toString(), asListOfString(e.getValue())))
                .filter(e -> !e.getValue().isEmpty())
                .toList();
            appendMapValues(type, variable, mapOfStrings);
        } else {
            appendValue(type, variable, value.toString());
        }
    }

    private List<String> asListOfString(Object some) {
        return some instanceof Iterable<?> i
            ? CollectionUtils.iterableToList(i).stream().filter(Objects::nonNull).map(String::valueOf).toList()
            : List.of(some.toString());
    }

    private void appendMapValues(UriTemplateParser.ExpressionType type, UriTemplateParser.Variable variable, List<Map.Entry<String, List<String>>> entries) {
        if (variable.explode()) {
            for (Map.Entry<String, List<String>> entry : entries) {
                appendKeyValues(type, variable, entry.getKey(), entry.getValue());
            }
            return;
        }
        if (type.isQueryPart()) {
            appendKeyValues(type, variable, variable.name(), aggregate(entries));
        } else {
            appendOperator(type);
            appendValues(type, variable, aggregate(entries));
        }
    }

    private List<String> aggregate(List<Map.Entry<String, List<String>>> entries) {
        return entries
            .stream()
            .flatMap(e -> Stream.concat(Stream.of(e.getKey()), e.getValue().stream()))
            .toList();
    }

    private void appendListValues(UriTemplateParser.ExpressionType type, UriTemplateParser.Variable variable, List<String> values) {
        if (variable.explode()) {
            for (String value : values) {
                appendValue(type, variable, value);
            }
            return;
        }
        if (type.isQueryPart()) {
            appendKeyValues(type, variable, variable.name(), values);
        } else {
            appendOperator(type);
            appendValues(type, variable, values);
        }
    }

    private void appendKeyValues(UriTemplateParser.ExpressionType type, UriTemplateParser.Variable variable, String key, List<String> values) {
        appendOperator(type);
        builder.append(key);
        builder.append('=');
        appendValues(type, variable, values);
    }

    private void appendValues(UriTemplateParser.ExpressionType type, UriTemplateParser.Variable variable, List<String> values) {
        for (Iterator<String> iterator = values.iterator(); iterator.hasNext(); ) {
            String value = iterator.next();
            value = applyModifier(value, variable.modifier());
            builder.append(type.isEncode() ? encode(value, type.isQueryPart()) : escape(value));
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
    }

    private void appendValue(UriTemplateParser.ExpressionType type, UriTemplateParser.Variable variable, String value) {
        value = applyModifier(value, variable.modifier());
        appendOperator(type);
        if (type.isQueryPart()) {
            builder.append(variable.name());
            if (StringUtils.isNotEmpty(value) || type != UriTemplateParser.ExpressionType.PATH_STYLE_PARAMETER_EXPANSION) {
                builder.append('=');
                builder.append(encode(value, true));
            }
        } else {
            builder.append(type.isEncode() ? encode(value, false) : escape(value));
        }
    }

    private void appendOperator(UriTemplateParser.ExpressionType type) {
        char separator = type.getSeparator();
        if (type == UriTemplateParser.ExpressionType.NONE) {
            appendSeparator(type, separator);
        } else if (type == UriTemplateParser.ExpressionType.FORM_STYLE_PARAMETER_EXPANSION) {
            builder.append(queryParamSeparator());
        } else {
            appendSeparator(type, separator);
            if (type == UriTemplateParser.ExpressionType.FRAGMENT_EXPANSION) {
                if (!hashStarted) {
                    hashStarted = true;
                    builder.append(type.getOperator());
                }
            } else if (type != UriTemplateParser.ExpressionType.RESERVED_EXPANSION) {
                builder.append(type.getOperator());
            }
        }
    }

    private void appendSeparator(UriTemplateParser.ExpressionType type, char separator) {
        // Append separator for previous value
        if (!needsSeparator) {
            needsSeparator = true;
        } else if (separator != type.getOperator()) {
            builder.append(separator);
        }
    }

    private char queryParamSeparator() {
        if (queryStarted) {
            return '&';
        }
        queryStarted = true;
        return '?';
    }

    private String applyModifier(String value, String modifier) {
        if (modifier == null) {
            return value;
        }
        try {
            int limit = Integer.parseInt(modifier.trim(), 10);
            if (limit < value.length()) {
                return value.substring(0, limit);
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        return value;
    }

    private String encode(String str, boolean query) {
        String encoded = URLEncoder.encode(str, StandardCharsets.UTF_8);
        return query ? encoded : encoded.replace("+", "%20");
    }

    private String escape(String v) {
        return v.replace("%", "%25").replaceAll("\\s", "%20");
    }

    private Object expandPOJO(Object found) {
        // Check for common expanded types, such as list or Map
        if (found instanceof Iterable || found instanceof Map) {
            return found;
        }
        // If a simple value, just use that
        if (found == null || ClassUtils.isJavaLangType(found.getClass())) {
            return found;
        }
        // Otherwise, expand the object into properties (after all, the user asked for an expanded parameter)
        return BeanMap.of(found);
    }

}
