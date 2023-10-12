/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalQuery;
import java.util.Date;
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
@TypeHint(
        value = {
                Duration.class,
                TemporalAmount.class,
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
@Internal
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
                                return Optional.of(Duration.ofSeconds(Integer.parseInt(amount)));
                            case 'm':
                                String ms = matcher.group(MILLIS);
                                if (StringUtils.hasText(ms)) {
                                    return Optional.of(Duration.ofMillis(Integer.parseInt(amount)));
                                } else {
                                    return Optional.of(Duration.ofMinutes(Integer.parseInt(amount)));
                                }
                            case 'h':
                                return Optional.of(Duration.ofHours(Integer.parseInt(amount)));
                            case 'd':
                                return Optional.of(Duration.ofDays(Integer.parseInt(amount)));
                            default:
                                final String seq = g2 + matcher.group(3);
                                switch (seq) {
                                    case "ns":
                                        return Optional.of(Duration.ofNanos(Integer.parseInt(amount)));
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

        // TemporalAccessor -> CharSequence
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

        /*
         * There's a not-a-bug <a href="https://bugs.openjdk.org/browse/JDK-8069324">JDK-8069324</a>
         * which unexpectedly fail because offset is mandatory for RFC_1123
         * To work around this we use special converter for LocalDateTime treating it as UTC timezone
         */
        final TypeConverter<LocalDateTime, CharSequence> localDateTimeConverter = (object, targetType, context) -> {
            try {
                DateTimeFormatter formatter = resolveFormatter(context);
                // Treat missing zone as UTC
                return Optional.of(formatter.format(object.atOffset(ZoneOffset.UTC)));
            } catch (DateTimeParseException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        };
        conversionService.addConverter(
            LocalDateTime.class,
            CharSequence.class,
            localDateTimeConverter
        );

        addTemporalStringConverter(conversionService, Instant.class, DateTimeFormatter.ISO_INSTANT, Instant::from);
        addTemporalStringConverter(conversionService, LocalDate.class, DateTimeFormatter.ISO_LOCAL_DATE, LocalDate::from);
        addTemporalStringConverter(conversionService, LocalDateTime.class, DateTimeFormatter.ISO_LOCAL_DATE_TIME, LocalDateTime::from);
        addTemporalStringConverter(conversionService, OffsetTime.class, DateTimeFormatter.ISO_OFFSET_TIME, OffsetTime::from);
        addTemporalStringConverter(conversionService, OffsetDateTime.class, DateTimeFormatter.ISO_OFFSET_DATE_TIME, OffsetDateTime::from);
        addTemporalStringConverter(conversionService, ZonedDateTime.class, DateTimeFormatter.ISO_ZONED_DATE_TIME, ZonedDateTime::from);

        // java.time -> Date
        addTemporalToDateConverter(conversionService, Instant.class, Function.identity());
        addTemporalToDateConverter(conversionService, OffsetDateTime.class, OffsetDateTime::toInstant);
        addTemporalToDateConverter(conversionService, ZonedDateTime.class, ZonedDateTime::toInstant);
        // these two are a bit icky, but required for yaml parsing compatibility
        // TODO Micronaut 4 Consider deletion
        addTemporalToDateConverter(conversionService, LocalDate.class, ld -> ld.atTime(0, 0).toInstant(ZoneOffset.UTC));
        addTemporalToDateConverter(conversionService, LocalDateTime.class, ldt -> ldt.toInstant(ZoneOffset.UTC));
    }

    private <T extends TemporalAccessor> void addTemporalStringConverter(ConversionService<?> conversionService, Class<T> temporalType, DateTimeFormatter isoFormatter, TemporalQuery<T> query) {
        conversionService.addConverter(CharSequence.class, temporalType, (CharSequence object, Class<T> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            // try explicit format first
            Optional<String> format = context.getAnnotationMetadata().stringValue(Format.class);
            if (format.isPresent()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format.get(), context.getLocale());
                try {
                    T converted = formatter.parse(object, query);
                    return Optional.of(converted);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            } else {
                try {
                    T converted = isoFormatter.parse(object, query);
                    return Optional.of(converted);
                } catch (DateTimeParseException ignored) {
                }
                // fall back to RFC 1123 date time for compatibility
                try {
                    T result = DateTimeFormatter.RFC_1123_DATE_TIME.parse(object, query);
                    return Optional.of(result);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        });
    }

    private <T extends TemporalAccessor> void addTemporalToDateConverter(ConversionService<?> conversionService, Class<T> temporalType, Function<T, Instant> toInstant) {
        conversionService.addConverter(temporalType, Date.class, (T object, Class<Date> targetType, ConversionContext context) -> Optional.of(Date.from(toInstant.apply(object))));
    }

    private DateTimeFormatter resolveFormatter(ConversionContext context) {
        Optional<String> format = context.getAnnotationMetadata().stringValue(Format.class);
        return format
            .map(pattern -> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
            .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
