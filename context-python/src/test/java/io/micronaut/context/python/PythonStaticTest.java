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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void mixedLiteralsKeepEachElementsKind() {
        assertEquals("[1, 2.5]", PythonStatic.repr(PythonStatic.list(1L, 2.5)));
        assertEquals("[1.0, 2.5]", PythonStatic.repr(PythonStatic.list(1.0, 2.5)));
    }
}
