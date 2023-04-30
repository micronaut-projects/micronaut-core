/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.context.env.exp;

import io.micronaut.context.env.PropertyExpressionResolver;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.value.ValueException;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The property expression for random values.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class RandomPropertyExpressionResolver implements PropertyExpressionResolver {

    private static final String RANDOM_PREFIX = "random.";
    private static final String MSG_INVALID_RANGE = "Invalid range: `";

    @Override
    public <T> Optional<T> resolve(PropertyResolver propertyResolver, ConversionService conversionService, String expression, Class<T> requiredType) {
        expression = expression.toLowerCase(Locale.ROOT);
        if (!expression.startsWith(RANDOM_PREFIX)) {
            return Optional.empty();
        }
        String value = expression.substring(RANDOM_PREFIX.length()).toLowerCase();
        return Optional.of(conversionService.convertRequired(resolveRandomValue(value, expression), requiredType));
    }

    private Object resolveRandomValue(String value, String expression) {
        switch (value) {
            case "port" -> {
                return SocketUtils.findAvailableTcpPort();
            }
            case "int", "integer" -> {
                return LazyInit.RANDOM.nextInt();
            }
            case "long" -> {
                return LazyInit.RANDOM.nextLong();
            }
            case "float" -> {
                return LazyInit.RANDOM.nextFloat();
            }
            case "shortuuid" -> {
                return UUID.randomUUID().toString().substring(25, 35);
            }
            case "uuid" -> {
                return UUID.randomUUID();
            }
            case "uuid2" -> {
                return UUID.randomUUID().toString().replace("-", "");
            }
            default -> {
                Matcher matcher = LazyInit.RANGE_PATTERN.matcher(value);
                if (matcher.find()) {
                    String rangeType = matcher.group(1).trim().toLowerCase();
                    String range = matcher.group(2);
                    if (range != null) {
                        range = range.substring(1, range.length() - 1);
                        switch (rangeType) {
                            case "int", "integer" -> {
                                return getNextIntegerInRange(range, expression);
                            }
                            case "long" -> {
                                return getNextLongInRange(range, expression);
                            }
                            case "float" -> {
                                return getNextFloatInRange(range, expression);
                            }
                            default -> getNextIntegerInRange(range, expression);
                        }
                    }
                }
                throw new ConfigurationException("Invalid random expression: " + expression);
            }
        }
    }

    private int getNextIntegerInRange(String range, String expression) {
        try {
            String[] tokens = range.split(",");
            int lowerBound = Integer.parseInt(tokens[0]);
            if (tokens.length == 1) {
                return (lowerBound >= 0 ? 1 : -1) * LazyInit.RANDOM.nextInt(Math.abs(lowerBound));
            }
            int upperBound = Integer.parseInt(tokens[1]);
            return LazyInit.RANDOM.nextInt(lowerBound, upperBound);
        } catch (NumberFormatException ex) {
            throw new ValueException(MSG_INVALID_RANGE + range + "` found for type Integer for expression: " + expression, ex);
        }
    }

    private long getNextLongInRange(String range, String expression) {
        try {
            String[] tokens = range.split(",");
            long lowerBound = Long.parseLong(tokens[0]);
            if (tokens.length == 1) {
                return (lowerBound >= 0 ? 1 : -1) * LazyInit.RANDOM.nextLong(Math.abs(lowerBound));
            }
            long upperBound = Long.parseLong(tokens[1]);
            return LazyInit.RANDOM.nextLong(lowerBound, upperBound);
        } catch (NumberFormatException ex) {
            throw new ValueException(MSG_INVALID_RANGE + range + "` found for type Long for expression: " + expression, ex);
        }
    }

    private float getNextFloatInRange(String range, String expression) {
        try {
            String[] tokens = range.split(",");
            float lowerBound = Float.parseFloat(tokens[0]);
            if (tokens.length == 1) {
                return (lowerBound >= 0 ? 1 : -1) * LazyInit.RANDOM.nextFloat(Math.abs(lowerBound));
            }
            float upperBound = Float.parseFloat(tokens[1]);
            return LazyInit.RANDOM.nextFloat(lowerBound, upperBound);
        } catch (NumberFormatException ex) {
            throw new ValueException(MSG_INVALID_RANGE + range + "` found for type Float for expression: " + expression, ex);
        }
    }

    static class LazyInit {
        private LazyInit() {}
        private static final SecureRandom RANDOM = new SecureRandom();
        private static final String RANDOM_UPPER_LIMIT = "(\\(-?\\d+(\\.\\d+)?\\))";
        private static final String RANDOM_RANGE = "(\\[-?\\d+(\\.\\d+)?,\\s?-?\\d+(\\.\\d+)?])";
        private static final Pattern RANGE_PATTERN = Pattern.compile("\\s?(\\S+?)(" + RANDOM_UPPER_LIMIT + "|" + RANDOM_RANGE + ")");
    }

}
