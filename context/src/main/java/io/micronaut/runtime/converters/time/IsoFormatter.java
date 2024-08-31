package io.micronaut.runtime.converters.time;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

/**
 * Just copied DateTimeFormatters form 'java.time' package.
 *
 * @since 4.7.0
 */
public class IsoFormatter {

    /**
     * Copy of java.time.Year.PARSER DateTimeFormatter.
     */
    public static final DateTimeFormatter ISO_YEAR = new DateTimeFormatterBuilder()
        .parseLenient()
        .appendValue(YEAR, 1, 10, SignStyle.NORMAL)
        .toFormatter();

    /**
     * Copy of java.time.YearMonth.PARSER DateTimeFormatter.
     */
    public static final DateTimeFormatter ISO_YEAR_MONTH = new DateTimeFormatterBuilder()
        .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
        .appendLiteral('-')
        .appendValue(MONTH_OF_YEAR, 2)
        .toFormatter();

    /**
     * Copy of java.time.MonthDay.PARSER DateTimeFormatter.
     */
    public static final DateTimeFormatter ISO_MONTH_DAY = new DateTimeFormatterBuilder()
        .appendLiteral("--")
        .appendValue(MONTH_OF_YEAR, 2)
        .appendLiteral('-')
        .appendValue(DAY_OF_MONTH, 2)
        .toFormatter();

    private IsoFormatter() {
    }
}
