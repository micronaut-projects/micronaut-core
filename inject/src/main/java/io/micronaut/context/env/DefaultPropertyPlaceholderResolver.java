/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default {@link PropertyPlaceholderResolver}.
 *
 * @author graemerocher
 * @since 1.0
 */
public class DefaultPropertyPlaceholderResolver implements PropertyPlaceholderResolver {

    /**
     * Prefix for placeholder in properties.
     */
    public static final String PREFIX = "${";

    /**
     * Suffix for placeholder in properties.
     */
    public static final String SUFFIX = "}";

    private static final Pattern ESCAPE_SEQUENCE = Pattern.compile("(.+)?:`([^`]+?)`");
    private static final Pattern ENVIRONMENT_VAR_SEQUENCE = Pattern.compile("^[\\p{Lu}_{0-9}]+");
    private static final char COLON = ':';

    private final PropertyResolver environment;
    private final String prefix;

    /**
     * @param environment The property resolver for the environment
     */
    public DefaultPropertyPlaceholderResolver(PropertyResolver environment) {
        this.environment = environment;
        this.prefix = PREFIX;
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }

    @Override
    public Optional<String> resolvePlaceholders(String str) {
        try {
            int i = str.indexOf(prefix);
            if (i > -1) {
                return Optional.of(resolvePlaceholders(str, i));
            }
            return Optional.of(str);
        } catch (ConfigurationException e) {
            return Optional.empty();
        }
    }

    @Override
    public String resolveRequiredPlaceholders(String str) throws ConfigurationException {
        int i = str.indexOf(prefix);
        if (i > -1) {
            return resolvePlaceholders(str, i);
        }
        return str;
    }

    private String resolvePlaceholders(String str, int startIndex) {
        StringBuilder builder = new StringBuilder(str.substring(0, startIndex));
        String restOfString = str.substring(startIndex + 2);
        int i = restOfString.indexOf('}');
        if (i > -1) {
            String expr = restOfString.substring(0, i).trim();
            if (restOfString.length() > i) {
                restOfString = restOfString.substring(i + 1);
            }
            resolveExpression(builder, str, expr);

            i = restOfString.indexOf(prefix);
            if (i > -1) {
                builder.append(resolvePlaceholders(restOfString, i));
            } else {
                builder.append(restOfString);
            }
        } else {
            throw new ConfigurationException("Incomplete placeholder definitions detected: " + str);
        }
        return builder.toString();
    }

    private void resolveExpression(StringBuilder builder, String str, String expr) {
        String defaultValue = null;
        Matcher matcher = ESCAPE_SEQUENCE.matcher(expr);

        boolean escaped = false;
        if (matcher.find()) {
            defaultValue = matcher.group(2);
            expr = matcher.group(1);
            escaped = true;
        } else {
            int j = expr.indexOf(COLON);
            if (j > -1) {
                defaultValue = expr.substring(j + 1);
                expr = expr.substring(0, j);
            }
        }

        if (environment.containsProperty(expr)) {
            String finalExpr = expr;
            builder.append(environment.getProperty(expr, String.class).orElseThrow(() -> new ConfigurationException("Could not resolve placeholder ${" + finalExpr + "} in value: " + str)));
            return;
        }
        if (ENVIRONMENT_VAR_SEQUENCE.matcher(expr).matches()) {
            String v = System.getenv(expr);
            if (StringUtils.isNotEmpty(v)) {
                builder.append(v);
                return;
            }
        }
        if (defaultValue != null) {
            if (!escaped && (ESCAPE_SEQUENCE.matcher(defaultValue).find() || defaultValue.indexOf(COLON) > -1)) {
                StringBuilder resolved = new StringBuilder();
                resolveExpression(resolved, expr, defaultValue);
                builder.append(resolved);
            } else {
                builder.append(defaultValue);
            }
            return;
        }
        throw new ConfigurationException("Could not resolve placeholder ${" + expr + "} in value: " + str);
    }
}
