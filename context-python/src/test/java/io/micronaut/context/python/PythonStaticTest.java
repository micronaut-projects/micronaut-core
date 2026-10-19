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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonStaticTest {

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
}
