/*
 * Copyright (C) 2012- Frode Carlsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *MapClaims.java
 * Note: rewritten to standard Java 8 DateTime by zemiak (c) 2016
 * Forked from: https://github.com/frode-carlsen/cron
 */

package io.micronaut.scheduling.cron;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This provides cron support for Java 8 using java-time.
 * <p>
 * <p>
 * Parser for unix-like cron expressions: Cron expressions allow specifying combinations of criteria for time
 * such as: &quot;Each Monday-Friday at 08:00&quot; or &quot;Every last friday of the month at 01:30&quot;
 * <p>
 * A cron expressions consists of 5 or 6 mandatory fields (seconds may be omitted) separated by space. <br>
 * These are:
 * <p>
 * <table cellspacing="8">
 * <tr>
 * <th align="left">Field</th>
 * <th align="left">&nbsp;</th>
 * <th align="left">Allowable values</th>
 * <th align="left">&nbsp;</th>
 * <th align="left">Special Characters</th>
 * </tr>
 * <tr>
 * <td align="left"><code>Seconds (may be omitted)</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>0-59</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Minutes</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>0-59</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Hours</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>0-23</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Day of month</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>1-31</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>, - * ? / L W</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Month</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>1-12 or JAN-DEC (note: english abbreviations)</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Day of week</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>1-7 or MON-SUN (note: english abbreviations)</code></td>
 * <td align="left">&nbsp;</td>
 * <td align="left"><code>, - * ? / L #</code></td>
 * </tr>
 * </table>
 * <p>
 * <p>
 * '*' Can be used in all fields and means 'for all values'. E.g. &quot;*&quot; in minutes, means 'for all minutes'
 * <p>
 * '?' Can be used in Day-of-month and Day-of-week fields. Used to signify 'no special value'. It is used when one want
 * to specify something for one of those two fields, but not the other.
 * <p>
 * '-' Used to specify a time interval. E.g. &quot;10-12&quot; in Hours field means 'for hours 10, 11 and 12'
 * <p>
 * ',' Used to specify multiple values for a field. E.g. &quot;MON,WED,FRI&quot; in Day-of-week field means &quot;for
 * monday, wednesday and friday&quot;
 * <p>
 * '/' Used to specify increments. E.g. &quot;0/15&quot; in Seconds field means &quot;for seconds 0, 15, 30, ad
 * 45&quot;. And &quot;5/15&quot; in seconds field means &quot;for seconds 5, 20, 35, and 50&quot;. If '*' s specified
 * before '/' it is the same as saying it starts at 0. For every field there's a list of values that can be turned on or
 * off. For Seconds and Minutes these range from 0-59. For Hours from 0 to 23, For Day-of-month it's 1 to 31, For Months
 * 1 to 12. &quot;/&quot; character helsp turn some of these values back on. Thus &quot;7/6&quot; in Months field
 * specify just Month 7. It doesn't turn on every 6 month following, since cron fields never roll over
 * <p>
 * 'L' Can be used on Day-of-month and Day-of-week fields. It signifies last day of the set of allowed values. In
 * Day-of-month field it's the last day of the month (e.g.. 31 jan, 28 feb (29 in leap years), 31 march, etc.). In
 * Day-of-week field it's Sunday. If there's a prefix, this will be subtracted (5L in Day-of-month means 5 days before
 * last day of Month: 26 jan, 23 feb, etc.)
 * <p>
 * 'W' Can be specified in Day-of-Month field. It specifies closest weekday (monday-friday). Holidays are not accounted
 * for. &quot;15W&quot; in Day-of-Month field means 'closest weekday to 15 i in given month'. If the 15th is a Saturday,
 * it gives Friday. If 15th is a Sunday, the it gives following Monday.
 * <p>
 * '#' Can be used in Day-of-Week field. For example: &quot;5#3&quot; means 'third friday in month' (day 5 = friday, #3
 * - the third). If the day does not exist (e.g. &quot;5#5&quot; - 5th friday of month) and there aren't 5 fridays in
 * the month, then it won't match until the next month with 5 fridays.
 * <p>
 * <b>Case-sensitive</b> No fields are case-sensitive
 * <p>
 * <b>Dependencies between fields</b> Fields are always evaluated independently, but the expression doesn't match until
 * the constraints of each field are met. Overlap of intervals are not allowed. That is: for
 * Day-of-week field &quot;FRI-MON&quot; is invalid,but &quot;FRI-SUN,MON&quot; is valid
 */
public final class CronExpression {

    /**
     * Represents a field in the cron expression.
     */
    enum CronFieldType {
        SECOND(0, 59, null),
        MINUTE(0, 59, null),
        HOUR(0, 23, null),
        DAY_OF_MONTH(1, 31, null),
        MONTH(1, 12,
                Arrays.asList("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")),
        DAY_OF_WEEK(1, 7,
                Arrays.asList("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"));

        final int from, to;
        final List<String> names;

        /**
         * Create a new cron field with given value.
         *
         * @param from  The minimum value
         * @param to    The maximum value
         * @param names The name assigned to each unit
         */
        CronFieldType(int from, int to, List<String> names) {
            this.from = from;
            this.to = to;
            this.names = names;
        }
    }

    private static final int CRON_EXPRESSION_LENGTH_WITH_SEC = 6;
    private static final int CRON_EXPRESSION_LENGTH_WITHOUT_SEC = 5;
    private static final int FOUR = 4;

    private final String expr;
    private final SimpleField secondField;
    private final SimpleField minuteField;
    private final SimpleField hourField;
    private final DayOfWeekField dayOfWeekField;
    private final SimpleField monthField;
    private final DayOfMonthField dayOfMonthField;

    private CronExpression(final String expr) {
        if (expr == null) {
            throw new IllegalArgumentException("expr is null"); //$NON-NLS-1$
        }

        this.expr = expr;

        final String[] parts = expr.split("\\s+"); //$NON-NLS-1$
        if (parts.length < CRON_EXPRESSION_LENGTH_WITHOUT_SEC || parts.length > CRON_EXPRESSION_LENGTH_WITH_SEC) {
            throw new IllegalArgumentException(String.format("Invalid cron expression [%s], expected 5 or 6 fields, got %s", expr, parts.length));
        }
        boolean withSeconds = parts.length == CRON_EXPRESSION_LENGTH_WITH_SEC;

        int ix = withSeconds ? 1 : 0;
        this.secondField = new SimpleField(CronFieldType.SECOND, withSeconds ? parts[0] : "0");
        this.minuteField = new SimpleField(CronFieldType.MINUTE, parts[ix++]);
        this.hourField = new SimpleField(CronFieldType.HOUR, parts[ix++]);
        this.dayOfMonthField = new DayOfMonthField(parts[ix++]);
        this.monthField = new SimpleField(CronFieldType.MONTH, parts[ix++]);
        this.dayOfWeekField = new DayOfWeekField(parts[ix++]);
    }

    /**
     * Create object from the String expression.
     *
     * @param expr The cron expression
     * @return The {@link CronExpression} instance
     */
    public static CronExpression create(final String expr) {
        return new CronExpression(expr);
    }

    /**
     * This will search for the next time within the next 4 years. If there is no
     * time matching, an InvalidArgumentException will be thrown (it is very
     * likely that the cron expression is invalid, like the February 30th).
     *
     * @param afterTime A date-time with a time-zone in the ISO-8601 calendar system
     * @return The next time within next 4 years
     */
    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime) {
        return nextTimeAfter(afterTime, afterTime.plusYears(FOUR));
    }

    /**
     * This will search for the next time within the next durationInMillis
     * millisecond. Be aware that the duration is specified in millis,
     * but in fact the limit is checked on a day-to-day basis.
     *
     * @param afterTime        A date-time with a time-zone in the ISO-8601 calendar system
     * @param durationInMillis The maximum duration in millis after a given time
     * @return The next time within given duration
     */
    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime, long durationInMillis) {
        return nextTimeAfter(afterTime, afterTime.plus(Duration.ofMillis(durationInMillis)));
    }

    /**
     * This will search for the next time within the given dateTimeBarrier.
     *
     * @param afterTime       A date-time with a time-zone in the ISO-8601 calendar system
     * @param dateTimeBarrier The upper limit or maximum date-time to check for next time
     * @return The next time within given barrier
     */
    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime, ZonedDateTime dateTimeBarrier) {
        ZonedDateTime nextTime = ZonedDateTime.from(afterTime).withNano(0).plusSeconds(1).withNano(0);

        while (true) { // day of week
            while (true) { // month
                while (true) { // day of month
                    while (true) { // hour
                        while (true) { // minute
                            while (true) { // second
                                if (secondField.matches(nextTime.getSecond())) {
                                    break;
                                }
                                nextTime = nextTime.plusSeconds(1).withNano(0);
                            }
                            if (minuteField.matches(nextTime.getMinute())) {
                                break;
                            }
                            nextTime = nextTime.plusMinutes(1).withSecond(0).withNano(0);
                        }
                        if (hourField.matches(nextTime.getHour())) {
                            break;
                        }
                        nextTime = nextTime.plusHours(1).withMinute(0).withSecond(0).withNano(0);
                    }
                    if (dayOfMonthField.matches(nextTime.toLocalDate())) {
                        break;
                    }
                    nextTime = nextTime.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    checkIfDateTimeBarrierIsReached(nextTime, dateTimeBarrier);
                }
                if (monthField.matches(nextTime.getMonth().getValue())) {
                    break;
                }
                nextTime = nextTime.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                checkIfDateTimeBarrierIsReached(nextTime, dateTimeBarrier);
            }
            if (dayOfWeekField.matches(nextTime.toLocalDate())) {
                break;
            }
            nextTime = nextTime.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            checkIfDateTimeBarrierIsReached(nextTime, dateTimeBarrier);
        }

        return nextTime;
    }

    private static void checkIfDateTimeBarrierIsReached(ZonedDateTime nextTime, ZonedDateTime dateTimeBarrier) {
        if (nextTime.isAfter(dateTimeBarrier)) {
            throw new IllegalArgumentException("No next execution time could be determined that is before the limit of " + dateTimeBarrier);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + expr + ">";
    }

    /**
     * A class that represent a cron field part.
     */
    static class FieldPart {

        private Integer from;
        private Integer to;
        private Integer increment;
        private String modifier;
        private String incrementModifier;
    }

    /**
     * A class that represent a basic field part of the cron field.
     */
    abstract static class BasicField {
        private static final Pattern CRON_FIELD_REGEXP = Pattern
                .compile("(?:                                             # start of group 1\n"
                                + "   (?:(?<all>\\*)|(?<ignore>\\?)|(?<last>L))  # global flag (L, ?, *)\n"
                                + " | (?<start>[0-9]{1,2}|[a-z]{3,3})              # or start number or symbol\n"
                                + "      (?:                                        # start of group 2\n"
                                + "         (?<mod>L|W)                             # modifier (L,W)\n"
                                + "       | -(?<end>[0-9]{1,2}|[a-z]{3,3})        # or end nummer or symbol (in range)\n"
                                + "      )?                                         # end of group 2\n"
                                + ")                                              # end of group 1\n"
                                + "(?:(?<incmod>/|\\#)(?<inc>[0-9]{1,7}))?        # increment and increment modifier (/ or \\#)\n",
                        Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);

        private static final int PART_INCREMENT = 999;

        final CronFieldType fieldType;

        /**
         * Represent parts for the cron field.
         */
        final List<FieldPart> parts = new ArrayList<>();

        private BasicField(CronFieldType fieldType, String fieldExpr) {
            this.fieldType = fieldType;
            parse(fieldExpr);
        }

        /**
         * Create a {@link BasicField} from the given String expression.
         *
         * @param fieldExpr String expression for a field
         */
        private void parse(String fieldExpr) { // NOSONAR
            String[] rangeParts = fieldExpr.split(",");
            for (String rangePart : rangeParts) {
                Matcher m = CRON_FIELD_REGEXP.matcher(rangePart);
                if (!m.matches()) {
                    throw new IllegalArgumentException("Invalid cron field '" + rangePart + "' for field [" + fieldType + "]");
                }
                String startNummer = m.group("start");
                String modifier = m.group("mod");
                String sluttNummer = m.group("end");
                String incrementModifier = m.group("incmod");
                String increment = m.group("inc");

                FieldPart part = new FieldPart();
                part.increment = PART_INCREMENT;
                if (startNummer != null) {
                    part.from = mapValue(startNummer);
                    part.modifier = modifier;
                    if (sluttNummer != null) {
                        part.to = mapValue(sluttNummer);
                        part.increment = 1;
                    } else if (increment != null) {
                        part.to = fieldType.to;
                    } else {
                        part.to = part.from;
                    }
                } else if (m.group("all") != null) {
                    part.from = fieldType.from;
                    part.to = fieldType.to;
                    part.increment = 1;
                } else if (m.group("ignore") != null) {
                    part.modifier = m.group("ignore");
                } else if (m.group("last") != null) {
                    part.modifier = m.group("last");
                } else {
                    throw new IllegalArgumentException("Invalid cron part: " + rangePart);
                }

                if (increment != null) {
                    part.incrementModifier = incrementModifier;
                    part.increment = Integer.valueOf(increment);
                }

                validateRange(part);
                validatePart(part);
                parts.add(part);

            }
        }

        /**
         * Validate the cron field part.
         *
         * @param part The part of cron-field
         */
        protected void validatePart(FieldPart part) {
            if (part.modifier != null) {
                throw new IllegalArgumentException(String.format("Invalid modifier [%s]", part.modifier));
            } else if (part.incrementModifier != null && !"/".equals(part.incrementModifier)) {
                throw new IllegalArgumentException(String.format("Invalid increment modifier [%s]", part.incrementModifier));
            }
        }

        /**
         * Validate range of the cron-field part.
         *
         * @param part The part of cron-field
         */
        private void validateRange(FieldPart part) {
            if ((part.from != null && part.from < fieldType.from) || (part.to != null && part.to > fieldType.to)) {
                throw new IllegalArgumentException(String.format("Invalid interval [%s-%s], must be %s<=_<=%s", part.from, part.to, fieldType.from,
                        fieldType.to));
            } else if (part.from != null && part.to != null && part.from > part.to) {
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid interval [%s-%s].  Rolling periods are not supported (ex. 5-1, only 1-5) since this won't give a deterministic result. Must be %s<=_<=%s",
                                part.from, part.to, fieldType.from, fieldType.to));
            }
        }

        /**
         * Map value to the {@link CronFieldType} name.
         *
         * @param value The value to map to names of {@link CronFieldType}
         * @return The integer value of name from the names for cron-field type
         */
        protected Integer mapValue(String value) {
            Integer idx;
            if (fieldType.names != null) {
                idx = fieldType.names.indexOf(value.toUpperCase(Locale.getDefault()));
                if (idx >= 0) {
                    return idx + 1;
                }
            }
            return Integer.valueOf(value);
        }

        /**
         * @param val  The value
         * @param part Cron field part to match to
         * @return True/False if the value matches the field part
         */
        protected boolean matches(int val, FieldPart part) {
            if (val >= part.from && val <= part.to && (val - part.from) % part.increment == 0) {
                return true;
            }
            return false;
        }
    }

    /**
     * A class that represent a simple cron field.
     */
    static class SimpleField extends BasicField {

        /**
         * Create a simple field type for the given field type and expression.
         *
         * @param fieldType The type of field, eg: month, day, second etc.
         * @param fieldExpr The field expression
         */
        SimpleField(CronFieldType fieldType, String fieldExpr) {
            super(fieldType, fieldExpr);
        }

        /**
         * Check if the given value matches the SimpleField.
         *
         * @param val The cron-field value
         * @return Whether the value matches
         */
        public boolean matches(int val) {
            if (val >= fieldType.from && val <= fieldType.to) {
                for (FieldPart part : parts) {
                    if (matches(val, part)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * A class that represents day of the week field in the cron expression.
     */
    static class DayOfWeekField extends BasicField {

        static final int DAYS_IN_WEEK = 7;

        /**
         * Construct the field from the given expression.
         *
         * @param fieldExpr The field expression
         */
        DayOfWeekField(String fieldExpr) {
            super(CronFieldType.DAY_OF_WEEK, fieldExpr);
        }

        /**
         * Check if the date matches the day of the week.
         *
         * @param date The date
         * @return Whether the date matches the day of the field
         */
        boolean matches(LocalDate date) {
            for (FieldPart part : parts) {
                if ("L".equals(part.modifier)) {
                    YearMonth ym = YearMonth.of(date.getYear(), date.getMonth().getValue());
                    return date.getDayOfWeek() == DayOfWeek.of(part.from) && date.getDayOfMonth() > (ym.lengthOfMonth() - DAYS_IN_WEEK);
                } else if ("#".equals(part.incrementModifier)) {
                    if (date.getDayOfWeek() == DayOfWeek.of(part.from)) {
                        int num = date.getDayOfMonth() / DAYS_IN_WEEK;
                        return part.increment == (date.getDayOfMonth() % DAYS_IN_WEEK == 0 ? num : num + 1);
                    }
                    return false;
                } else if (matches(date.getDayOfWeek().getValue(), part)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected Integer mapValue(String value) {
            // Use 1-7 for weedays, but 0 will also represent sunday (linux practice)
            return "0".equals(value) ? Integer.valueOf(DAYS_IN_WEEK) : super.mapValue(value);
        }

        @Override
        protected boolean matches(int val, FieldPart part) {
            return "?".equals(part.modifier) || super.matches(val, part);
        }

        @Override
        protected void validatePart(FieldPart part) {
            if (part.modifier != null && Arrays.asList("L", "?").indexOf(part.modifier) == -1) {
                throw new IllegalArgumentException(String.format("Invalid modifier [%s]", part.modifier));
            } else if (part.incrementModifier != null && Arrays.asList("/", "#").indexOf(part.incrementModifier) == -1) {
                throw new IllegalArgumentException(String.format("Invalid increment modifier [%s]", part.incrementModifier));
            }
        }
    }

    /**
     * A class that represent the Day of Month field in the cron expression.
     */
    static class DayOfMonthField extends BasicField {

        static final int WEEK_DAYS = 5;
        static final int FIRST_DAY = 1;
        static final int ONE_DAY = 1;

        /**
         * Construct the Day of Month field from the given expression.
         *
         * @param fieldExpr The field expression
         */
        DayOfMonthField(String fieldExpr) {
            super(CronFieldType.DAY_OF_MONTH, fieldExpr);
        }

        /**
         * Check if the given date matches the day in the month.
         *
         * @param date The date
         * @return Whether the date matches the day in the month
         */
        boolean matches(LocalDate date) {
            for (FieldPart part : parts) {
                if ("L".equals(part.modifier)) {
                    YearMonth ym = YearMonth.of(date.getYear(), date.getMonth().getValue());
                    return date.getDayOfMonth() == (ym.lengthOfMonth() - (part.from == null ? 0 : part.from));
                } else if ("W".equals(part.modifier)) {
                    if (date.getDayOfWeek().getValue() <= WEEK_DAYS) {
                        if (date.getDayOfMonth() == part.from) {
                            return true;
                        } else if (date.getDayOfWeek().getValue() == WEEK_DAYS) {
                            return date.plusDays(ONE_DAY).getDayOfMonth() == part.from;
                        } else if (date.getDayOfWeek().getValue() == FIRST_DAY) {
                            return date.minusDays(ONE_DAY).getDayOfMonth() == part.from;
                        }
                    }
                } else if (matches(date.getDayOfMonth(), part)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void validatePart(FieldPart part) {
            if (part.modifier != null && Arrays.asList("L", "W", "?").indexOf(part.modifier) == -1) {
                throw new IllegalArgumentException(String.format("Invalid modifier [%s]", part.modifier));
            } else if (part.incrementModifier != null && !"/".equals(part.incrementModifier)) {
                throw new IllegalArgumentException(String.format("Invalid increment modifier [%s]", part.incrementModifier));
            }
        }

        @Override
        protected boolean matches(int val, FieldPart part) {
            return "?".equals(part.modifier) || super.matches(val, part);
        }
    }
}
