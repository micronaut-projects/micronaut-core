/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aware Python {@code datetime} values and the Java types that hold an absolute instant
 * ({@link Instant}, {@link OffsetDateTime}, {@link ZonedDateTime}): micronaut-projects/pyronaut#334.
 */
class PythonAwareDateTimeConversionTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void anAwareDatetimeConvertsToTheJavaTypesOfAnInstant() {
        Value utc = eval("datetime.datetime(2026, 10, 7, 12, 34, 56, 123456, tzinfo=datetime.timezone.utc)");
        Value offset = eval("datetime.datetime(2026, 10, 7, 18, 4, 56, 123456, tzinfo=datetime.timezone(datetime.timedelta(hours=5, minutes=30)))");
        Instant instant = Instant.parse("2026-10-07T12:34:56.123456Z");

        assertEquals(instant, utc.as(Instant.class));
        assertEquals(instant, offset.as(Instant.class));
        assertEquals(OffsetDateTime.of(2026, 10, 7, 18, 4, 56, 123_456_000, ZoneOffset.ofHoursMinutes(5, 30)), offset.as(OffsetDateTime.class));
        assertEquals(ZonedDateTime.of(2026, 10, 7, 12, 34, 56, 123_456_000, ZoneOffset.UTC), utc.as(ZonedDateTime.class));
        assertEquals(instant, PythonConversion.convertValue(utc, Instant.class));
    }

    @Test
    void aNamedRegionIsKeptForAZonedDateTime() {
        // the name pytz gives its zones; zoneinfo calls it "key" (covered below when tzdata is present)
        Value value = eval("""
            datetime.datetime(2026, 10, 7, 12, tzinfo=type('Madrid', (datetime.tzinfo,), {
                'zone': 'Europe/Madrid',
                'utcoffset': lambda self, dt: datetime.timedelta(hours=2),
                'dst': lambda self, dt: datetime.timedelta(hours=1),
                'tzname': lambda self, dt: 'CEST',
            })())""");

        assertEquals(ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneId.of("Europe/Madrid")), value.as(ZonedDateTime.class));
    }

    @Test
    void aZoneinfoRegionIsKeptForAZonedDateTime() {
        assumeZoneinfo();
        Value value = eval("datetime.datetime(2026, 10, 7, 12, tzinfo=__import__('zoneinfo').ZoneInfo('Europe/Madrid'))");

        assertEquals(ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneId.of("Europe/Madrid")), value.as(ZonedDateTime.class));
    }

    @Test
    void aNaiveDatetimeIsRefusedWhereAnInstantIsExpected() {
        Value naive = eval("datetime.datetime(2026, 10, 7)");

        for (Class<?> type : List.of(Instant.class, OffsetDateTime.class, ZonedDateTime.class)) {
            // the mapping does not take a naive value, so a Java overload for LocalDateTime is still chosen for it
            assertThrows(RuntimeException.class, () -> naive.as(type));
        }
    }

    @Test
    void anAwareDatetimeIsStillRefusedWhereALocalDateTimeIsExpected() {
        Value aware = eval("datetime.datetime(2026, 10, 7, tzinfo=datetime.timezone.utc)");

        PolyglotException e = assertThrows(PolyglotException.class, () -> aware.as(LocalDateTime.class));
        assertTrue(e.getMessage().contains("Annotated[datetime, Instant]"), e.getMessage());
    }

    @Test
    void anAwareDatetimeReachesAnErasedParameterAsAnOffsetDateTime() {
        PythonConversionTest.ErasedParameter parameter = new PythonConversionTest.ErasedParameter();
        context.getBindings("python").putMember("parameter", parameter);
        context.eval("python", """
            import datetime
            parameter.accept(datetime.datetime(2026, 10, 7, 12, tzinfo=datetime.timezone.utc))
            parameter.accept(datetime.datetime(2026, 10, 7, 12, tzinfo=datetime.timezone(datetime.timedelta(hours=-3))))
            parameter.accept(datetime.datetime(2026, 10, 7, 12))
            """);

        assertEquals(
            List.of(
                OffsetDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.ofHours(-3)),
                LocalDateTime.of(2026, 10, 7, 12, 0)
            ),
            parameter.received()
        );
    }

    @Test
    void javaInstantsBecomeAwarePythonDatetimes() {
        Value isoformat = eval("lambda value: (type(value).__module__ + '.' + type(value).__name__, value.isoformat())");

        assertConvertsTo(isoformat, Instant.parse("2026-10-07T12:34:56.123456789Z"), "2026-10-07T12:34:56.123456+00:00");
        assertConvertsTo(isoformat, OffsetDateTime.of(2026, 10, 7, 18, 4, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), "2026-10-07T18:04:00+05:30");
        assertConvertsTo(isoformat, ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.ofHours(-3)), "2026-10-07T12:00:00-03:00");
    }

    @Test
    void anAwareDatetimeRoundTripsThroughEachJavaType() {
        Value original = eval("datetime.datetime(2026, 10, 7, 18, 4, 56, 123456, tzinfo=datetime.timezone(datetime.timedelta(hours=5, minutes=30)))");
        Value equal = eval("lambda a, b: a == b and a.utcoffset() is not None and b.utcoffset() is not None");

        for (Class<?> type : List.of(Instant.class, OffsetDateTime.class, ZonedDateTime.class, Object.class)) {
            Object java = original.as(type);
            Value back = context.asValue(PythonCoercion.coerceToContext(PythonCoercion.pythonDateTime(java), context));
            assertTrue(equal.execute(original, back).asBoolean(), type + " -> " + java);
        }
    }

    @Test
    void anUntaggedJavaInstantStaysAJavaObject() {
        // Python code declaring the Java type (date: Annotated[ZonedDateTime, Header]) hands it to Java APIs
        ZonedDateTime header = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneOffset.UTC);

        Value value = context.asValue(PythonCoercion.coerceToContext(header, context));

        assertTrue(value.isHostObject());
        assertEquals(header, value.asHostObject());
        assertEquals(header, context.asValue(PythonCoercion.coerceToContext(header, context, ZonedDateTime.class)).asHostObject());
    }

    @Test
    void aJavaRegionBecomesAZoneinfoZone() {
        assumeZoneinfo();
        Value key = eval("lambda value: value.tzinfo.key");
        ZonedDateTime madrid = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneId.of("Europe/Madrid"));

        Value value = context.asValue(PythonCoercion.coerceToContext(PythonCoercion.pythonDateTime(madrid), context));

        assertEquals("Europe/Madrid", key.execute(value).asString());
        assertEquals(madrid, value.as(ZonedDateTime.class));
    }

    @Test
    void anOverloadedJavaMethodIsChosenByWhetherTheDatetimeIsAware() {
        context.getBindings("python").putMember("api", new Overloads());

        assertEquals("local:2026-10-07T12:00", eval("api.describe(datetime.datetime(2026, 10, 7, 12))").asString());
        assertEquals("instant:2026-10-07T12:00:00Z", eval("api.describe(datetime.datetime(2026, 10, 7, 12, tzinfo=datetime.timezone.utc))").asString());
    }

    private void assertConvertsTo(Value isoformat, Object java, String expected) {
        Value value = context.asValue(PythonCoercion.coerceToContext(PythonCoercion.pythonDateTime(java), context));
        Value result = isoformat.execute(value);
        assertEquals("datetime.datetime", result.getArrayElement(0).asString());
        assertEquals(expected, result.getArrayElement(1).asString());
    }

    private void assumeZoneinfo() {
        context.eval("python", """
            def _zoneinfo_available():
                try:
                    __import__('zoneinfo').ZoneInfo('Europe/Madrid')
                    return True
                except Exception:
                    return False
            """);
        boolean available = context.eval("python", "_zoneinfo_available()").asBoolean();
        Assumptions.assumeTrue(available, "no time zone data for zoneinfo");
    }

    private Value eval(String expression) {
        context.eval("python", "import datetime");
        return context.eval("python", expression);
    }

    public static final class Overloads {

        public String describe(LocalDateTime value) {
            return "local:" + value;
        }

        public String describe(Instant value) {
            return "instant:" + value;
        }
    }
}
