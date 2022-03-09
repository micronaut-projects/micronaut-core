/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.runtime.converters.time;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
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
                TemporalAmount.class,
                Instant.class,
                LocalDate.class,
                LocalTime.class,
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
        final BiFunction<CharSequence, ConversionContext, Optional<Duration>> durationConverter = (object, context) -> {
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
        };

        // CharSequence -> Duration
        conversionService.addConverter(
            CharSequence.class,
            Duration.class,
            (object, targetType, context) -> durationConverter.apply(object, context)
        );

        // CharSequence -> TemporalAmount
        conversionService.addConverter(
            CharSequence.class,
            TemporalAmount.class,
            (object, targetType, context) -> durationConverter.apply(object, context).map(TemporalAmount.class::cast)
        );

        // CharSequence -> LocalDateTime
        conversionService.addConverter(
            CharSequence.class,
            LocalDateTime.class,
            (object, targetType, context) -> {
                try {
                    return format(context,
                            object,
                            LocalDateTime::parse,
                            LocalDateTime::parse);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        );

        // CharSequence -> Instant
        conversionService.addConverter(
                CharSequence.class,
                Instant.class,
                (object, targetType, context) -> {
                    try {
                        return format(context,
                                object,
                                (obj, formatter) -> ZonedDateTime.parse(object, formatter).toInstant(),
                                Instant::parse);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );

        // TemporalAccessor - CharSequence
        final TypeConverter<TemporalAccessor, CharSequence> temporalConverter = (object, targetType, context) -> {
            try {
                return format(context, object, (obj, formatter) -> formatter.format(obj), Object::toString);
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
                    return format(context,
                            object,
                            LocalDate::parse,
                            LocalDate::parse);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        );

        // CharSequence -> LocalTime
        conversionService.addConverter(
                CharSequence.class,
                LocalTime.class,
                (object, targetType, context) -> {
                    try {
                        return format(context,
                                object,
                                LocalTime::parse,
                                LocalTime::parse);
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
                    return format(context,
                            object,
                            ZonedDateTime::parse,
                            ZonedDateTime::parse);
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
                        return format(context,
                                object,
                                OffsetDateTime::parse,
                                OffsetDateTime::parse);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> OffsetTime
        conversionService.addConverter(
                CharSequence.class,
                OffsetTime.class,
                (object, targetType, context) -> {
                    try {
                        return format(context,
                                object,
                                OffsetTime::parse,
                                OffsetTime::parse);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );
    }

    private <Y, T> Optional<Y> format(ConversionContext context,
                                      T value,
                                      BiFunction<T, DateTimeFormatter, Y> formatDate,
                                      Function<T, Y> defaultFormat) {
        return Optional.of(context.getFormat().map(pattern -> {
            if (ConversionContext.RFC_1123_FORMAT.equals(pattern)) {
                T converted = value;
                if (converted instanceof TemporalAccessor) {
                    converted = (T) toGmt((TemporalAccessor) converted);
                }
                return formatDate.apply(converted, DateTimeFormatter.RFC_1123_DATE_TIME);
            } else {
                return formatDate.apply(value, DateTimeFormatter.ofPattern(pattern, context.getLocale()));
            }
        }).orElseGet(() -> defaultFormat.apply(value)));
    }

    private TemporalAccessor toGmt(TemporalAccessor value) {
        ZoneId gmt = ZoneId.of("GMT");
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).withZoneSameInstant(gmt);
        } else if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).atZoneSameInstant(gmt);
        } else if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(gmt);
        } else if (value instanceof Instant) {
            return ((Instant) value).atZone(gmt);
        }
        return value;
    }
}
