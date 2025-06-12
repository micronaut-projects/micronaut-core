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
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.util.StringUtils;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalQuery;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

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
        LocalTime.class,
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

    private static final Pattern PERIOD_MATCHER = Pattern.compile("^(-?\\d+)([unywmMdD])(s?)$");
    private static final Pattern DURATION_MATCHER = Pattern.compile("^(-?\\d+)([unsmhd])(s?)$");
    private static final int MILLIS = 3;

    /**
     * Copy of java.time.Year.PARSER DateTimeFormatter.
     */
    private static final DateTimeFormatter ISO_YEAR = new DateTimeFormatterBuilder()
        .parseLenient()
        .appendValue(YEAR, 1, 10, SignStyle.NORMAL)
        .toFormatter();

    /**
     * Copy of java.time.YearMonth.PARSER DateTimeFormatter.
     */
    private static final DateTimeFormatter ISO_YEAR_MONTH = new DateTimeFormatterBuilder()
        .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
        .appendLiteral('-')
        .appendValue(MONTH_OF_YEAR, 2)
        .toFormatter();

    /**
     * Copy of java.time.MonthDay.PARSER DateTimeFormatter.
     */
    private static final DateTimeFormatter ISO_MONTH_DAY = new DateTimeFormatterBuilder()
        .appendLiteral("--")
        .appendValue(MONTH_OF_YEAR, 2)
        .appendLiteral('-')
        .appendValue(DAY_OF_MONTH, 2)
        .toFormatter();

    private final Map<String, DateTimeFormatter> formattersCache = new ConcurrentHashMap<>();

    @NextMajorVersion("Consider deletion of LocalDate and LocalDateTime converters")
    @Override
    public void register(MutableConversionService conversionService) {
        final BiFunction<CharSequence, ConversionContext, Optional<Duration>> durationConverter = durationConverter();

        // CharSequence -> Duration
        conversionService.addConverter(
            CharSequence.class,
            Duration.class,
            (object, targetType, context) -> durationConverter.apply(object, context)
        );

        // Integer -> Duration
        conversionService.addConverter(
            Integer.class,
            Duration.class,
            (integer, targetType, context) -> durationConverter.apply(integer.toString(), context)
        );

        final BiFunction<CharSequence, ConversionContext, Optional<Period>> periodConverter = periodConverter();

        // CharSequence -> Period
        conversionService.addConverter(
            CharSequence.class,
            Period.class,
            (object, targetType, context) -> periodConverter.apply(object, context)
        );

        // Integer -> Period
        conversionService.addConverter(
            Integer.class,
            Period.class,
            (integer, targetType, context) -> periodConverter.apply(integer.toString(), context)
        );

        // CharSequence -> TemporalAmount
        conversionService.addConverter(
            CharSequence.class,
            TemporalAmount.class,
            (object, targetType, context) -> {
                var str = object.toString();
                if (str.contains("y")
                    || str.contains("M")
                    || str.contains("w")
                    || str.contains("D")
                ) {
                    return periodConverter.apply(object, context).map(TemporalAmount.class::cast);
                }
                return durationConverter.apply(object, context).map(TemporalAmount.class::cast);
            }
        );

        addTemporalStringConverters(conversionService, Instant.class, DateTimeFormatter.ISO_INSTANT, Instant::from);
        addTemporalStringConverters(conversionService, LocalDate.class, DateTimeFormatter.ISO_LOCAL_DATE, LocalDate::from);
        addTemporalStringConverters(conversionService, LocalDateTime.class, DateTimeFormatter.ISO_LOCAL_DATE_TIME, LocalDateTime::from);
        addTemporalStringConverters(conversionService, OffsetTime.class, DateTimeFormatter.ISO_OFFSET_TIME, OffsetTime::from);
        addTemporalStringConverters(conversionService, OffsetDateTime.class, DateTimeFormatter.ISO_OFFSET_DATE_TIME, OffsetDateTime::from);
        addTemporalStringConverters(conversionService, ZonedDateTime.class, DateTimeFormatter.ISO_ZONED_DATE_TIME, ZonedDateTime::from);

        addTemporalStringConverters(conversionService, YearMonth.class, ISO_YEAR_MONTH, YearMonth::from);
        addTemporalStringConverters(conversionService, Year.class, ISO_YEAR, Year::from);
        addTemporalIntegerConverters(conversionService, Year.class, ISO_YEAR, Year::from);
        addTemporalStringConverters(conversionService, MonthDay.class, ISO_MONTH_DAY, MonthDay::from);
        addTemporalStringConverters(conversionService, LocalTime.class, DateTimeFormatter.ISO_LOCAL_TIME, LocalTime::from);

        conversionService.addConverter(CharSequence.class, ZoneId.class, (object, targetType, context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                ZoneId result = ZoneId.of(object.toString());
                return Optional.of(result);
            } catch (DateTimeParseException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });
        conversionService.addConverter(ZoneId.class, CharSequence.class, (object, targetType, context) -> {
            if (Objects.isNull(object)) {
                return Optional.empty();
            }
            return Optional.of(object.toString());
        });

        // java.time -> Date
        addTemporalToDateConverter(conversionService, Instant.class, Function.identity());
        addTemporalToDateConverter(conversionService, OffsetDateTime.class, OffsetDateTime::toInstant);
        addTemporalToDateConverter(conversionService, ZonedDateTime.class, ZonedDateTime::toInstant);
        // these two are a bit icky, but required for yaml parsing compatibility
        // TODO Micronaut 4 Consider deletion
        addTemporalToDateConverter(conversionService, LocalDate.class, ld -> ld.atTime(0, 0).toInstant(ZoneOffset.UTC));
        addTemporalToDateConverter(conversionService, LocalDateTime.class, ldt -> ldt.toInstant(ZoneOffset.UTC));
    }

    private <T extends TemporalAccessor> void addTemporalStringConverters(MutableConversionService conversionService, Class<T> temporalType, DateTimeFormatter isoFormatter, TemporalQuery<T> query) {
        conversionService.addConverter(CharSequence.class, temporalType, (CharSequence object, Class<T> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            // try explicit format first
            Optional<String> format = context.getAnnotationMetadata().stringValue(Format.class);
            if (format.isPresent()) {
                DateTimeFormatter formatter = getFormatter(format.get(), context);
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
            }
            // fall back to RFC 1123 date time for compatibility
            try {
                T result = DateTimeFormatter.RFC_1123_DATE_TIME.parse(object, query);
                return Optional.of(result);
            } catch (DateTimeParseException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        conversionService.addConverter(temporalType, CharSequence.class, (object, targetType, context) -> {
            if (Objects.isNull(object)) {
                return Optional.empty();
            }
            // try explicit format first
            Optional<String> format = context.getAnnotationMetadata().stringValue(Format.class);
            if (format.isPresent()) {
                DateTimeFormatter formatter = getFormatter(format.get(), context);
                try {
                    CharSequence converted = formatter.format(object);
                    return Optional.of(converted);
                } catch (DateTimeException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            } else {
                try {
                    CharSequence converted = isoFormatter.format(object);
                    return Optional.of(converted);
                } catch (DateTimeException ignored) {
                }
            }
            // fall back to RFC 1123 date time for compatibility
            try {
                CharSequence converted = DateTimeFormatter.RFC_1123_DATE_TIME.format(object);
                return Optional.of(converted);
            } catch (DateTimeException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });
    }

    private <T extends TemporalAccessor> void addTemporalIntegerConverters(MutableConversionService conversionService, Class<T> temporalType, DateTimeFormatter isoFormatter, TemporalQuery<T> query) {
        conversionService.addConverter(Integer.class, temporalType, (Integer object, Class<T> targetType, ConversionContext context) -> {
            if (Objects.isNull(object)) {
                return Optional.empty();
            }
            // try explicit format first
            Optional<String> format = context.getAnnotationMetadata().stringValue(Format.class);
            if (format.isPresent()) {
                DateTimeFormatter formatter = getFormatter(format.get(), context);
                try {
                    T converted = formatter.parse(object.toString(), query);
                    return Optional.of(converted);
                } catch (DateTimeParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            } else {
                try {
                    T converted = isoFormatter.parse(object.toString(), query);
                    return Optional.of(converted);
                } catch (DateTimeParseException ignored) {
                }
            }
            // fall back to RFC 1123 date time for compatibility
            try {
                T result = DateTimeFormatter.RFC_1123_DATE_TIME.parse(object.toString(), query);
                return Optional.of(result);
            } catch (DateTimeParseException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });
    }

    private BiFunction<CharSequence, ConversionContext, Optional<Duration>> durationConverter() {
        return (object, context) -> {
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
                            case 's' -> {
                                return Optional.of(Duration.ofSeconds(Integer.parseInt(amount)));
                            }
                            case 'm' -> {
                                String ms = matcher.group(MILLIS);
                                if (StringUtils.hasText(ms)) {
                                    return Optional.of(Duration.ofMillis(Integer.parseInt(amount)));
                                } else {
                                    return Optional.of(Duration.ofMinutes(Integer.parseInt(amount)));
                                }
                            }
                            case 'h' -> {
                                return Optional.of(Duration.ofHours(Integer.parseInt(amount)));
                            }
                            case 'd' -> {
                                return Optional.of(Duration.ofDays(Integer.parseInt(amount)));
                            }
                            default -> {
                                final String seq = g2 + matcher.group(3);
                                if (seq.equals("ns")) {
                                    return Optional.of(Duration.ofNanos(Integer.parseInt(amount)));
                                } else if (seq.equals("us")) {
                                    return Optional.of(Duration.ofNanos(Integer.parseInt(amount) * 1000L));
                                }
                                context.reject(
                                    value,
                                    new DateTimeParseException("Unparseable date format (" + value + "). Should either be a ISO-8601 duration or a round number followed by the unit type", value, 0)
                                );
                                return Optional.empty();
                            }
                        }
                    } catch (NumberFormatException e) {
                        context.reject(value, e);
                    }
                } else {
                    context.reject(
                        value,
                        new DateTimeParseException("Unparseable date format (" + value + "). Should either be a ISO-8601 duration or a round number followed by the unit type", value, 0)
                    );
                }
            }
            return Optional.empty();
        };
    }

    private BiFunction<CharSequence, ConversionContext, Optional<Period>> periodConverter() {
        return (object, context) -> {
            String value = object.toString().trim();
            if (value.startsWith("P")) {
                try {
                    return Optional.of(Period.parse(value));
                } catch (DateTimeParseException e) {
                    context.reject(value, e);
                    return Optional.empty();
                }
            } else {
                Matcher matcher = PERIOD_MATCHER.matcher(value);
                if (matcher.find()) {
                    String amount = matcher.group(1);
                    final String g2 = matcher.group(2);
                    char type = g2.charAt(0);
                    try {
                        switch (type) {
                            case 'y' -> {
                                return Optional.of(Period.ofYears(Integer.parseInt(amount)));
                            }
                            case 'm', 'M' -> {
                                return Optional.of(Period.ofMonths(Integer.parseInt(amount)));
                            }
                            case 'w' -> {
                                return Optional.of(Period.ofWeeks(Integer.parseInt(amount)));
                            }
                            case 'd', 'D' -> {
                                return Optional.of(Period.ofDays(Integer.parseInt(amount)));
                            }
                            default -> {
                                context.reject(
                                    value,
                                    new DateTimeParseException("Unparseable date format (" + value + "). Should either be a ISO-8601 duration or a round number followed by the unit type", value, 0)
                                );
                                return Optional.empty();
                            }
                        }
                    } catch (NumberFormatException e) {
                        context.reject(value, e);
                    }
                } else {
                    context.reject(
                        value,
                        new DateTimeParseException("Unparseable date format (" + value + "). Should either be a ISO-8601 duration or a round number followed by the unit type", value, 0)
                    );
                }
            }
            return Optional.empty();
        };
    }

    private DateTimeFormatter getFormatter(String pattern, ConversionContext context) {
        var key = pattern + context.getLocale();
        var cachedFormatter = formattersCache.get(key);
        if (cachedFormatter != null) {
            return cachedFormatter;
        }
        var formatter = DateTimeFormatter.ofPattern(pattern, context.getLocale());
        formattersCache.put(key, formatter);

        return formatter;
    }

    private <T extends TemporalAccessor> void addTemporalToDateConverter(MutableConversionService conversionService, Class<T> temporalType, Function<T, Instant> toInstant) {
        conversionService.addConverter(temporalType, Date.class, (T object, Class<Date> targetType, ConversionContext context) -> Optional.of(Date.from(toInstant.apply(object))));
    }
}
