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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The helpers of compiled bodies answer what Python answers where Java would differ.
 */
final class PythonStaticTest {

    @Test
    void intOfAFloatTruncatesAndRaisesWherePythonDoes() {
        assertEquals(2, PythonStatic.toInt(2.9));
        assertEquals(-2, PythonStatic.toInt(-2.9));
        assertEquals(2, PythonStatic.toInt((Object) 2.9));
        assertThrows(IllegalArgumentException.class, () -> PythonStatic.toInt(Double.NaN));
        assertThrows(ArithmeticException.class, () -> PythonStatic.toInt(Double.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> PythonStatic.toInt(Double.NEGATIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> PythonStatic.toInt(1e300));
        assertEquals(12, PythonStatic.toInt("\u00a012 "));
    }

    @Test
    void tuplesRenderInParenthesesAndStayTuples() {
        List<Object> pair = PythonStatic.tuple(1L, "a");
        assertEquals("(1, 'a')", PythonStatic.repr(pair));
        assertEquals("(1,)", PythonStatic.repr(PythonStatic.tuple(1L)));
        assertEquals("()", PythonStatic.repr(PythonStatic.tuple()));
        assertEquals("[1, 'a']", PythonStatic.repr(PythonStatic.list(1L, "a")));
        assertEquals("(1, 2.5)", PythonStatic.repr(PythonStatic.copyOfTuple(List.of(1L, 2.5))));
        assertEquals("{'k': (1,)}", PythonStatic.repr(Map.of("k", PythonStatic.tuple(1L))));
        assertSame(pair, PythonStatic.copy(pair));
        assertEquals(List.of(1L, "a"), pair);
        assertThrows(UnsupportedOperationException.class, () -> pair.add(2L));
    }

    @Test
    void stripAndSplitUsePythonsWhitespace() {
        assertEquals("x", PythonStatic.strip("\u00a0x\u00a0"));
        assertEquals("x", PythonStatic.strip(" \t\u0085x\u3000\n"));
        assertEquals("", PythonStatic.strip("\u00a0"));
        assertEquals("a b", PythonStatic.strip("a b"));
        assertEquals(List.of("a", "b", "c"), PythonStatic.split("\u00a0a\u00a0b\u2003 c\u0085"));
        assertEquals(List.of(), PythonStatic.split(" \u00a0"));
        assertEquals(List.of("a", "b"), PythonStatic.split("a  b"));
    }

    @Test
    void recursiveContainersRenderWithPythonsMarkers() {
        List<Object> list = PythonStatic.list(1L);
        list.add(list);
        assertEquals("[1, [...]]", PythonStatic.repr(list));
        Map<Object, Object> map = PythonStatic.map("self", 1L);
        map.put("self", map);
        assertEquals("{'self': {...}}", PythonStatic.repr(map));
        List<Object> twice = PythonStatic.list(list, list);
        assertEquals("[[1, [...]], [1, [...]]]", PythonStatic.repr(twice));
        List<Object> shared = PythonStatic.list(1L);
        assertEquals("[[1], [1]]", PythonStatic.repr(PythonStatic.list(shared, shared)));
    }

    @Test
    void aTupleEqualsNoList() {
        List<Object> pair = PythonStatic.tuple(1L, 2L);
        List<Object> list = PythonStatic.list(1L, 2L);
        // (1, 2) in [[1, 2]] and [1, 2] in [(1, 2)] are False in Python
        assertFalse(PythonStatic.contains(PythonStatic.list(list), pair));
        assertFalse(PythonStatic.contains(PythonStatic.list(pair), list));
        assertTrue(PythonStatic.contains(PythonStatic.list(pair), PythonStatic.tuple(1L, 2L)));
        assertTrue(PythonStatic.contains(PythonStatic.list(list), PythonStatic.list(1L, 2L)));
        // nested: ([1],) in [((1,),)] is False, and so is a dict holding a tuple for one holding a list
        assertFalse(PythonStatic.contains(PythonStatic.list(PythonStatic.tuple(PythonStatic.tuple(1L))), PythonStatic.tuple(PythonStatic.list(1L))));
        assertFalse(PythonStatic.contains(PythonStatic.list(PythonStatic.map("k", pair)), PythonStatic.map("k", list)));
        assertTrue(PythonStatic.contains(PythonStatic.list(PythonStatic.map("k", pair)), PythonStatic.map("k", PythonStatic.tuple(1L, 2L))));
        assertTrue(PythonStatic.contains(PythonStatic.list("a", 1L), 1L));
        assertFalse(PythonStatic.contains(PythonStatic.list("a", 1L), "b"));
    }

    @Test
    void aListIsNoDictKeyNorSetElement() {
        Map<Object, Object> byPair = PythonStatic.map(PythonStatic.tuple(1L, 2L), "a");
        Set<Object> pairs = PythonStatic.set(PythonStatic.tuple(1L, 2L));
        assertEquals("a", PythonStatic.item(byPair, PythonStatic.tuple(1L, 2L)));
        assertTrue(PythonStatic.contains(pairs, PythonStatic.tuple(1L, 2L)));
        // Python raises TypeError: unhashable type: 'list', where Java would find the equal tuple
        assertThrows(ClassCastException.class, () -> PythonStatic.item(byPair, PythonStatic.list(1L, 2L)));
        assertThrows(ClassCastException.class, () -> PythonStatic.get(byPair, PythonStatic.list(1L, 2L), "b"));
        assertThrows(ClassCastException.class, () -> PythonStatic.contains(byPair, PythonStatic.list(1L, 2L)));
        assertThrows(ClassCastException.class, () -> PythonStatic.contains(pairs, PythonStatic.list(1L, 2L)));
        assertThrows(ClassCastException.class, () -> PythonStatic.add(pairs, PythonStatic.list(1L, 2L)));
        assertThrows(ClassCastException.class, () -> PythonStatic.setItem(byPair, PythonStatic.list(1L, 2L), "c"));
        assertThrows(ClassCastException.class, () -> PythonStatic.map(PythonStatic.list(1L), "a"));
        assertThrows(ClassCastException.class, () -> PythonStatic.set(PythonStatic.set(1L)));
        assertThrows(ClassCastException.class, () -> PythonStatic.copyOfSet(List.of(PythonStatic.list(1L))));
        assertThrows(ClassCastException.class, () -> PythonStatic.set(PythonStatic.tuple(PythonStatic.list(1L))));
        assertEquals(1, pairs.size());
        assertEquals(1, byPair.size());
    }

    @Test
    void stringsAreQuotedAsReprQuotesThem() {
        // repr('a\x85\xa0​\U000e0001é😀 \x7f\x00　 \ud800') in Python 3.13
        assertEquals("'a\\x85\\xa0\\u200b\\ue000\\U000e0001é😀\\u2028\\x7f\\x00\\u3000 \\ud800'",
            PythonStatic.repr("a\u0085 ​󠀁é😀 \u007f\u0000　 \uD800"));
        assertEquals("'it\\'s \"x\"'", PythonStatic.repr("it's \"x\""));
        assertEquals("\"it's\"", PythonStatic.repr("it's"));
        assertEquals("'\\\\ \\n\\r\\t'", PythonStatic.repr("\\ \n\r\t"));
    }

    @Test
    void mixedLiteralsKeepEachElementsKind() {
        assertEquals("[1, 2.5]", PythonStatic.repr(PythonStatic.list(1L, 2.5)));
        assertEquals("[1.0, 2.5]", PythonStatic.repr(PythonStatic.list(1.0, 2.5)));
    }

    @Test
    void intDivisionIsCorrectlyRoundedBeyondTwoToTheFiftyThree() {
        long big = (1L << 53) + (1L << 40) + 1;
        // Python: (2**53 + 2**40 + 1) / 3 == 3002766255456256.5, where (double) big / 3 rounds twice
        double expected = 3002766255456256.5;
        assertEquals(expected, new BigDecimal(big).divide(new BigDecimal(3), MathContext.DECIMAL128).doubleValue());
        assertEquals(expected, PythonStatic.divide(big, 3));
        assertEquals(-expected, PythonStatic.divide(-big, 3));
        assertEquals(-expected, PythonStatic.divide(big, -3));
        assertEquals(1.0, PythonStatic.divide(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(9.223372036854776E18, PythonStatic.divide(Long.MIN_VALUE, -1));
        assertEquals(0.5, PythonStatic.divide(1, 2));
        assertEquals(-0.0, PythonStatic.divide(0, -5));
        assertThrows(ArithmeticException.class, () -> PythonStatic.divide(1L << 60, 0));
    }

    @Test
    void anIntAndAFloatCompareExactly() {
        long above = (1L << 53) + 1;
        double rounded = 9007199254740992.0;  // (double) above
        assertTrue(PythonStatic.compare(above, rounded) > 0);
        assertTrue(PythonStatic.compare(-above, -rounded) < 0);
        assertEquals(0.0, PythonStatic.compare(1L << 60, (double) (1L << 60)));
        assertEquals(0.0, PythonStatic.compare(0, -0.0));
        assertTrue(PythonStatic.compare(Long.MAX_VALUE, Double.POSITIVE_INFINITY) < 0);
        assertTrue(Double.isNaN(PythonStatic.compare(above, Double.NaN)));
        assertTrue(PythonStatic.compare(3, 2.5) > 0);
    }

    @Test
    void concatenatingNoneRaisesAsPythonDoes() {
        assertEquals("ab", PythonStatic.concat("a", "b"));
        IllegalStateException left = assertThrows(IllegalStateException.class, () -> PythonStatic.concat(null, "b"));
        assertTrue(left.getMessage().startsWith("TypeError"), left.getMessage());
        IllegalStateException right = assertThrows(IllegalStateException.class, () -> PythonStatic.concat("a", null));
        assertTrue(right.getMessage().startsWith("TypeError"), right.getMessage());
    }

    @Test
    void veryLongLiteralsAreReadWithoutExhaustingTheStack() {
        // a backtracking matcher recursing once per digit group overflowed the stack on such literals
        String grouped = "1_".repeat(100_000) + "1";
        assertThrows(ArithmeticException.class, () -> PythonStatic.toInt(grouped));
        assertEquals(Double.POSITIVE_INFINITY, PythonStatic.toFloat(grouped));
        assertEquals(0.5, PythonStatic.toFloat("0." + "0".repeat(100_000) + "5e100000"), 0.0);
        assertEquals(1_000L, PythonStatic.toInt("1_000"));
        assertEquals(2.5e3, PythonStatic.toFloat("2_5.0e2"), 0.0);
        assertThrows(IllegalArgumentException.class, () -> PythonStatic.toInt("1__0"));
        assertThrows(IllegalArgumentException.class, () -> PythonStatic.toFloat("1_.5"));
    }
}
