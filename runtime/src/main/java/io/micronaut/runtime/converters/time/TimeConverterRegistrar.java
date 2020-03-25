/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.runtime.converters.time;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registers data time converters.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
// Android doesn't support java.time
@Requires(notEnv = Environment.ANDROID)
@BootstrapContextCompatible
@TypeHint(
        value = {
                Duration.class,
                Instant.class,
                LocalDate.class,
                LocalDateTime.class,
                MonthDay.class,
                OffsetDateTime.class,
                OffsetTime.class,
                Period.class,
                Year.class,
                YearMonth.class,
                ZonedDateTime.class,
                ZoneId.class,
                ZoneOffset.class
        },
        accessType = TypeHint.AccessType.ALL_PUBLIC
)
public class TimeConverterRegistrar implements TypeConverterRegistrar {

    private static final Pattern DURATION_MATCHER = Pattern.compile("^(-?\\d+)([unsmhd])(s?)$");
    private static final int MILLIS = 3;

    @Override
    public void register(ConversionService<?> conversionService) {
        // CharSequence -> Duration
        conversionService.addConverter(
            CharSequence.class,
            Duration.class,
            (object, targetType, context) -> {
                String value = object.toString().trim();
                if (value.startsWith("P")) {
                    try {
                        return Optional.of(Duration.parse(value));
                    } catch (DateTimeParseException e) {
                        context.reject(value, e);
                        return Optional.empty();
                    }
                } else {
                    Matcher matcher = DURATION_MATCHER.matcher(value);
                    if (matcher.find()) {
                        String amount = matcher.group(1);
                        final String g2 = matcher.group(2);
                        char type = g2.charAt(0);
                        try {
                            switch (type) {
                                case 's':
                                    return Optional.of(Duration.ofSeconds(Integer.valueOf(amount)));
                                case 'm':
                                    String ms = matcher.group(MILLIS);
                                    if (StringUtils.hasText(ms)) {
                                        return Optional.of(Duration.ofMillis(Integer.valueOf(amount)));
                                    } else {
                                        return Optional.of(Duration.ofMinutes(Integer.valueOf(amount)));
                                    }
                                case 'h':
                                    return Optional.of(Duration.ofHours(Integer.valueOf(amount)));
                                case 'd':
                                    return Optional.of(Duration.ofDays(Integer.valueOf(amount)));
                                default:
                                    final String seq = g2 + matcher.group(3);
                                    switch (seq) {
                                        case "ns":
                                            return Optional.of(Duration.ofNanos(Integer.valueOf(amount)));
                                        default:
                                            context.reject(
                                                    value,
                                                    new DateTimeParseException("Unparseable date format (" + value + "). Should either be a ISO-8601 duration or a round number followed by the unit type", value, 0));
                                            return Optional.empty();
                                    }
                            }
                        } catch (NumberFormatException e) {
                            context.reject(value, e);
                        }
                    }
                }
                return Optional.empty();
            }
        );

        // CharSequence -> LocalDateTime
        conversionService.addConverter(
            CharSequence.class,
            LocalDateTime.class,
            (object, targetType, context) -> {
                try {
                    DateTimeFormatter formatter = resolveFormatter(context);
                    LocalDateTime result = LocalDateTime.parse(object, formatter);
                    return Optional.of(result);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        );

        // TemporalAccessor - CharSequence
        final TypeConverter<TemporalAccessor, CharSequence> temporalConverter = (object, targetType, context) -> {
            try {
                DateTimeFormatter formatter = resolveFormatter(context);
                return Optional.of(formatter.format(object));
            } catch (DateTimeParseException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        };
        conversionService.addConverter(
                TemporalAccessor.class,
                CharSequence.class,
                temporalConverter
        );

        // CharSequence -> LocalDate
        conversionService.addConverter(
            CharSequence.class,
            LocalDate.class,
            (object, targetType, context) -> {
                try {
                    DateTimeFormatter formatter = resolveFormatter(context);
                    LocalDate result = LocalDate.parse(object, formatter);
                    return Optional.of(result);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        );


        // CharSequence -> ZonedDateTime
        conversionService.addConverter(
            CharSequence.class,
            ZonedDateTime.class,
            (object, targetType, context) -> {
                try {
                    DateTimeFormatter formatter = resolveFormatter(context);
                    ZonedDateTime result = ZonedDateTime.parse(object, formatter);
                    return Optional.of(result);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        );

        // CharSequence -> OffsetDateTime
        conversionService.addConverter(
                CharSequence.class,
                OffsetDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        OffsetDateTime result = OffsetDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );
    }

    private DateTimeFormatter resolveFormatter(ConversionContext context) {
        Optional<String> format = context.getAnnotationMetadata().stringValue(Format.class);
        return format
            .map((pattern) -> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
            .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
