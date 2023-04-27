package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class OperatorExpressionSpec extends AbstractEvaluatedExpressionsSpec {

    void "test '/' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 / 5 }",
                "#{ 5 / 10 }",
                "#{ 10 div 5 }",
                "#{ 5 div 10 }",
                "#{ 10 / 5 / 2 }",

                // long
                "#{ 10 / 5L }",
                "#{ 5l / 10 }",
                "#{ 10L div 5l }",
                "#{ 5 div 10L }",
                "#{ 10L div 5 / 2 }",

                // float
                "#{ 10f / 5 }",
                "#{ 5 / 10f }",
                "#{ 10f div 5F }",
                "#{ 5 div 10F }",

                // double
                "#{ 10d / 5 }",
                "#{ 5 / 10d }",
                "#{ 10d div 5D }",
                "#{ 5 div 10D }",

                // mixed
                "#{ 10d / 5f }",
                "#{ 5L / 10d }",
                "#{ 10L div 5f }"
        )

        expect:
        results[0] instanceof Integer && results[0] == 2
        results[1] instanceof Integer && results[1] == 0
        results[2] instanceof Integer && results[2] == 2
        results[3] instanceof Integer && results[3] == 0
        results[4] instanceof Integer && results[4] == 1

        results[5] instanceof Long && results[5] == 2
        results[6] instanceof Long && results[6] == 0
        results[7] instanceof Long && results[7] == 2
        results[8] instanceof Long && results[8] == 0
        results[9] instanceof Long && results[9] == 1

        results[10] instanceof Float && results[10] == 10f / 5
        results[11] instanceof Float && results[11] == 5 / 10f
        results[12] instanceof Float && results[12] == 10f / 5F
        results[13] instanceof Float && results[13] == 5 / 10F

        results[14] instanceof Double && results[14] == 10d / 5
        results[15] instanceof Double && results[15] == 5 / 10d
        results[16] instanceof Double && results[16] == 10d / 5D
        results[17] instanceof Double && results[17] == 5 / 10D

        results[18] instanceof Double && results[18] == 10d / 5f
        results[19] instanceof Double && results[19] == 5L / 10d
        results[20] instanceof Float && results[20] == 10L / 5f
    }

    void "test '%' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 % 5 }",  // 0
                "#{ 5 % 10 }",              // 1
                "#{ 10 mod 5 }",            // 2
                "#{ 5 mod 10 }",            // 3
                "#{ 10 % 5 % 3}",           // 4

                // long
                "#{ 10 % 5L }",             // 5
                "#{ 5l % 10 }",             // 6
                "#{ 10L mod 5l }",          // 7
                "#{ 5 mod 10L }",           // 8
                "#{ 13L % 5 mod 2 }",       // 9

                // float
                "#{ 10f % 5 }",             // 10
                "#{ 5 % 10f }",             // 11
                "#{ 10f mod 5F }",          // 12
                "#{ 5 mod 10F }",           // 13

                // double
                "#{ 10d % 5 }",             // 14
                "#{ 5 % 10d }",             // 15
                "#{ 10d mod 5D }",          // 16
                "#{ 5 mod 10D }",           // 17

                // mixed
                "#{ 10d % 5f }",            // 18
                "#{ 5L % 10d }",            // 19
                "#{ 10L mod 5f }"           // 20
        )


        expect:
        results[0] instanceof Integer && results[0] == 0
        results[1] instanceof Integer && results[1] == 5
        results[2] instanceof Integer && results[2] == 0
        results[3] instanceof Integer && results[3] == 5
        results[4] instanceof Integer && results[4] == 0

        results[5] instanceof Long && results[5] == 0
        results[6] instanceof Long && results[6] == 5
        results[7] instanceof Long && results[7] == 0
        results[8] instanceof Long && results[8] == 5
        results[9] instanceof Long && results[9] == 1

        results[10] instanceof Float && results[10] == 10f % 5
        results[11] instanceof Float && results[11] == 5 % 10f
        results[12] instanceof Float && results[12] == 10f % 5F
        results[13] instanceof Float && results[13] == 5 % 10F

        results[14] instanceof Double && results[14] == 10d % 5
        results[15] instanceof Double && results[15] == 5 % 10d
        results[16] instanceof Double && results[16] == 10d % 5D
        results[17] instanceof Double && results[17] == 5 % 10D

        results[18] instanceof Double && results[18] == 10d % 5f
        results[19] instanceof Double && results[19] == 5L % 10d
        results[20] instanceof Float && results[20] == 10L % 5f
    }

    void "test -' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 - 5 }",  // 0
                "#{ 5 - 10 }",              // 1
                "#{ 25 - 5 - 10 }",         // 2

                // long
                "#{ 10 - 5L }",             // 3
                "#{ 5l - 10 }",             // 4
                "#{ 10L - 5l }",            // 5
                "#{ 5 - 10L }",             // 6


                // float
                "#{ 10f - 5 }",             // 7
                "#{ 5 - 10f }",             // 8
                "#{ 10f - 5F }",            // 9
                "#{ 5 - 10F }",             // 10

                // double
                "#{ 10d - 5 }",             // 11
                "#{ 5 - 10d }",             // 12
                "#{ 10d - 5D }",            // 13
                "#{ 5 - 10D }",             // 14

                // mixed
                "#{ 10d - 5f }",            // 15
                "#{ 5L - 10d }",            // 16
                "#{ 10L - 5f }"             // 17
        )

        expect:
        results[0] instanceof Integer && results[0] == 5
        results[1] instanceof Integer && results[1] == -5
        results[2] instanceof Integer && results[2] == 10

        results[3] instanceof Long && results[3] == 10 - 5L
        results[4] instanceof Long && results[4] == 5l - 10
        results[5] instanceof Long && results[5] == 10L - 5l
        results[6] instanceof Long && results[6] == 5 - 10L

        results[7] instanceof Float && results[7] == 10f - 5
        results[8] instanceof Float && results[8] == 5 - 10f
        results[9] instanceof Float && results[9] == 10f - 5F
        results[10] instanceof Float && results[10] == 5 - 10F

        results[11] instanceof Double && results[11] == 10d - 5
        results[12] instanceof Double && results[12] == 5 - 10d
        results[13] instanceof Double && results[13] == 10d - 5D
        results[14] instanceof Double && results[14] == 5 - 10D

        results[15] instanceof Double && results[15] == 10d - 5f
        results[16] instanceof Double && results[16] == 5L - 10d
        results[17] instanceof Float && results[17] == 10L - 5f
    }

    void "test '*' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 * 5 }",    // 0
                "#{ -8 * 10 * 5 }",         // 1

                // long
                "#{ 10 * 5L }",             // 2
                "#{ 5l * 10 }",             // 3
                "#{ 10L * 5l }",            // 4
                "#{ 5 * 10L }",             // 5

                // float
                "#{ 10f * 5 }",             // 6
                "#{ 5 * 10f }",             // 7
                "#{ 10f * 5F }",            // 8
                "#{ 5 * 10F }",             // 9

                // double
                "#{ 10d * 5 }",             // 10
                "#{ 5 * 10d }",             // 11
                "#{ 10d * 5D }",            // 12
                "#{ 5 * 10D }",             // 13

                // mixed
                "#{ 10d * 5f }",            // 14
                "#{ 5L * 10d }",            // 15
                "#{ 10L * 5f }"             // 16
        )

        expect:
        results[0] instanceof Integer && results[0] == 50
        results[1] instanceof Integer && results[1] == -8 * 10 * 5

        results[2] instanceof Long && results[2] == 10 * 5L
        results[3] instanceof Long && results[3] == 5l * 10
        results[4] instanceof Long && results[4] == 10L * 5l
        results[5] instanceof Long && results[5] == 5 * 10L

        results[6] instanceof Float && results[6] == 10f * 5
        results[7] instanceof Float && results[7] == 5 * 10f
        results[8] instanceof Float && results[8] == 10f * 5F
        results[9] instanceof Float && results[9] == 5 * 10F

        results[10] instanceof Double && results[10] == 10d * 5
        results[11] instanceof Double && results[11] == 5 * 10d
        results[12] instanceof Double && results[12] == 10d * 5D
        results[13] instanceof Double && results[13] == 5 * 10D

        results[14] instanceof Double && results[14] == 10d * 5f
        results[15] instanceof Double && results[15] == 5L * 10d
        results[16] instanceof Float && results[16] == 10L * 5f
    }

    void "test '+' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 + 5 }",              // 0
                "#{ -8 + 10 + 5 }",                     // 1

                // long
                "#{ 10 + 5L }",                         // 2
                "#{ 5l + 10 }",                         // 3
                "#{ 10L + 5l }",                        // 4
                "#{ 5 + 10L }",                         // 5

                // float
                "#{ 10f + 5 }",                         // 6
                "#{ 5 + 10f }",                         // 7
                "#{ 10f + 5F }",                        // 8
                "#{ 5 + 10F }",                         // 9

                // double
                "#{ 10d + 5 }",                         // 10
                "#{ 5 + 10d }",                         // 11
                "#{ 10d + 5D }",                        // 12
                "#{ 5 + 10D }",                         // 13

                // mixed
                "#{ 10d + 5f }",                        // 14
                "#{ 5L + 10d }",                        // 15
                "#{ 10L + 5f }",                        // 16

                // string
                "#{ '1' + '2' }",                       // 17
                "#{ '1' + null }",                      // 18
                "#{ null + '1' }",                      // 19
                "#{ null + '1' + null + 2D }",          // 20
                "#{ 15 + 'str' + 2L }",                 // 21
                "#{ 2f + 'str' + 2 }",                  // 22
                "#{ .014 + 'str' + 2L + 'test' }",      // 23
                "#{ 1 + 2 + 'str' + 2L + 'test' }",     // 24
                "#{ 1 + 2 + 3 + 'str' }",               // 25
                "#{ 1 + 2 - 3 + 'str' }"                // 26
        )

        expect:
        results[0] instanceof Integer && results[0] == 10 + 5
        results[1] instanceof Integer && results[1] == -8 + 10 + 5

        results[2] instanceof Long && results[2] == 10 + 5L
        results[3] instanceof Long && results[3] == 5l + 10
        results[4] instanceof Long && results[4] == 10L + 5l
        results[5] instanceof Long && results[5] == 5 + 10L

        results[6] instanceof Float && results[6] == 10f + 5
        results[7] instanceof Float && results[7] == 5 + 10f
        results[8] instanceof Float && results[8] == 10f + 5F
        results[9] instanceof Float && results[9] == 5 + 10F

        results[10] instanceof Double && results[10] == 10d + 5
        results[11] instanceof Double && results[11] == 5 + 10d
        results[12] instanceof Double && results[12] == 10d + 5D
        results[13] instanceof Double && results[13] == 5 + 10D

        results[14] instanceof Double && results[14] == 10d + 5f
        results[15] instanceof Double && results[15] == 5L + 10d
        results[16] instanceof Float && results[16] == 10L + 5f

        results[17] instanceof String && results[17] == '12'
        results[18] instanceof String && results[18] == '1null'
        results[19] instanceof String && results[19] == 'null1'
        results[20] instanceof String && results[20] == 'null1null2.0'
        results[21] instanceof String && results[21] == '15str2'
        results[22] instanceof String && results[22] == '2.0str2'
        results[23] instanceof String && results[23] == '0.014str2test'
        results[24] instanceof String && results[24] == '3str2test'
        results[25] instanceof String && results[25] == '6str'
        results[26] instanceof String && results[26] == '0str'
    }

    void "test '>' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 > 5 }",    // 0
                "#{ -8 > -3 }",             // 1

                // long
                "#{ 10L > 5l }",            // 2
                "#{ 5 > 10L }",             // 3
                "#{ 10l > 5 }",             // 4

                // double
                "#{ 10d > 5 }",             // 5
                "#{ 5 > 10d }",             // 6
                "#{ 10D > 5d }",            // 7
                "#{ -.4 > 5d }",            // 8
                "#{ .123 > .1 }",           // 9
                "#{ .123 > .1229 }",        // 10

                // float
                "#{ 10f > 5 }",             // 11
                "#{ 5 > 10f }",             // 12
                "#{ 10F > 5f }",            // 13
                "#{ -.4f > 5f }",           // 14
                "#{ .123f > -5f }",         // 15

                // mixed
                "#{ 10f > 5d }",            // 16
                "#{ 5L > 10f }",            // 17
                "#{ 10L > 5D }",            // 18
                "#{ -.4 > 5l }",            // 19
                "#{ .123f > -5 }",          // 20
                "#{ 10L > 11 }"             // 21
        )

        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == false

        results[2] instanceof Boolean && results[2] == true
        results[3] instanceof Boolean && results[3] == false
        results[4] instanceof Boolean && results[4] == true

        results[5] instanceof Boolean && results[5] == true
        results[6] instanceof Boolean && results[6] == false
        results[7] instanceof Boolean && results[7] == true
        results[8] instanceof Boolean && results[8] == false
        results[9] instanceof Boolean && results[9] == true
        results[10] instanceof Boolean && results[10] == true

        results[11] instanceof Boolean && results[11] == true
        results[12] instanceof Boolean && results[12] == false
        results[13] instanceof Boolean && results[13] == true
        results[14] instanceof Boolean && results[14] == false
        results[15] instanceof Boolean && results[15] == true

        results[16] instanceof Boolean && results[16] == true
        results[17] instanceof Boolean && results[17] == false
        results[18] instanceof Boolean && results[18] == true
        results[19] instanceof Boolean && results[19] == false
        results[20] instanceof Boolean && results[20] == true
        results[21] instanceof Boolean && results[21] == false
    }

    void "test '<' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 < 5 }",    // 0
                "#{ -8 < -3 }",             // 1

                // long
                "#{ 10L < 5l }",            // 2
                "#{ 5 < 10L }",             // 3
                "#{ 10l < 5 }",             // 4

                // double
                "#{ 10d < 5 }",             // 5
                "#{ 5 < 10d }",             // 6
                "#{ 10D < 5d }",            // 7
                "#{ -.4 < 5d }",            // 8
                "#{ .123 < .1 }",           // 9
                "#{ .1229 < .123 }",        // 10

                // float
                "#{ 10f < 5 }",             // 11
                "#{ 5 < 10f }",             // 12
                "#{ 10F < 5f }",            // 13
                "#{ -.4f < 5f }",           // 14
                "#{ .123f < -5f }",         // 15

                // mixed
                "#{ 10f < 5d }",            // 16
                "#{ 5L < 10f }",            // 17
                "#{ 10L < 5D }",            // 18
                "#{ -.4 < 5l }",            // 19
                "#{ .123f < -5 }",          // 20
                "#{ 10L < 11 }"             // 21
        )

        expect:
        results[0] instanceof Boolean && results[0] == false
        results[1] instanceof Boolean && results[1] == true

        results[2] instanceof Boolean && results[2] == false
        results[3] instanceof Boolean && results[3] == true
        results[4] instanceof Boolean && results[4] == false

        results[5] instanceof Boolean && results[5] == false
        results[6] instanceof Boolean && results[6] == true
        results[7] instanceof Boolean && results[7] == false
        results[8] instanceof Boolean && results[8] == true
        results[9] instanceof Boolean && results[9] == false
        results[10] instanceof Boolean && results[10] == true

        results[11] instanceof Boolean && results[11] == false
        results[12] instanceof Boolean && results[12] == true
        results[13] instanceof Boolean && results[13] == false
        results[14] instanceof Boolean && results[14] == true
        results[15] instanceof Boolean && results[15] == false

        results[16] instanceof Boolean && results[16] == false
        results[17] instanceof Boolean && results[17] == true
        results[18] instanceof Boolean && results[18] == false
        results[19] instanceof Boolean && results[19] == true
        results[20] instanceof Boolean && results[20] == false
        results[21] instanceof Boolean && results[21] == true
    }

    void "test '>=' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                 // int
                "#{ 10 >= 5 }",   // 0
                "#{ -8 >= -3 }",            // 1
                "#{ 3 >= 3 }",              // 2

                // long
                "#{ 10L >= 5l }",           // 3
                "#{ 5 >= 10L }",            // 4
                "#{ 10l >= 5 }",            // 5
                "#{ 5l >= 5 }",             // 6

                // double
                "#{ 10d >= 5 }",            // 7
                "#{ 5 >= 10d }",            // 8
                "#{ 10D >= 5d }",           // 9
                "#{ -.4 >= 5d }",           // 10
                "#{ .123 >= .1 }",          // 11

                // float
                "#{ 10f >= 5 }",            // 12
                "#{ 5 >= 10f }",            // 13
                "#{ 10F >= 5f }",           // 14
                "#{ -.4f >= 5f }",          // 15
                "#{ .123f >= -5f }",        // 16

                // mixed
                "#{ 10f >= 5d }",           // 17
                "#{ 5L >= 10f }",           // 18
                "#{ 10L >= 5D }",           // 19
                "#{ -.4 >= 5l }",           // 20
                "#{ .123f >= -5 }",         // 21
                "#{ 12L >= 11 }",           // 22
                "#{ 11 >= 11L }"            // 23
        )

        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == false
        results[2] instanceof Boolean && results[2] == true

        results[3] instanceof Boolean && results[3] == true
        results[4] instanceof Boolean && results[4] == false
        results[5] instanceof Boolean && results[5] == true
        results[6] instanceof Boolean && results[6] == true

        results[7] instanceof Boolean && results[7] == true
        results[8] instanceof Boolean && results[8] == false
        results[9] instanceof Boolean && results[9] == true
        results[10] instanceof Boolean && results[10] == false
        results[11] instanceof Boolean && results[11] == true

        results[12] instanceof Boolean && results[12] == true
        results[13] instanceof Boolean && results[13] == false
        results[14] instanceof Boolean && results[14] == true
        results[15] instanceof Boolean && results[15] == false
        results[16] instanceof Boolean && results[16] == true

        results[17] instanceof Boolean && results[17] == true
        results[18] instanceof Boolean && results[18] == false
        results[19] instanceof Boolean && results[19] == true
        results[20] instanceof Boolean && results[20] == false
        results[21] instanceof Boolean && results[21] == true
        results[22] instanceof Boolean && results[22] == true
        results[23] instanceof Boolean && results[23] == true
    }

    void "test '<=' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 <= 5 }",     // 0
                "#{ -8 <= -3 }",                // 1
                "#{ 3 <= 3 }",                  // 2

                // long
                "#{ 10L <= 5l }",               // 3
                "#{ 5 <= 10L }",                // 4
                "#{ 10l <= 5 }",                // 5
                "#{ 5l <= 5 }",                 // 6

                // double
                "#{ 10d <= 5 }",                // 7
                "#{ 5 <= 10d }",                // 8
                "#{ 10D <= 5d }",               // 9
                "#{ -.4 <= 5d }",               // 10
                "#{ .123 <= .1 }",              // 11

                // float
                "#{ 10f <= 5 }",                // 12
                "#{ 5 <= 10f }",                // 13
                "#{ 10F <= 5f }",               // 14
                "#{ -.4f <= 5f }",              // 15
                "#{ .123f <= -5f }",            // 16

                // mixed
                "#{ 10f <= 5d }",               // 17
                "#{ 5L <= 10f }",               // 18
                "#{ 10L <= 5D }",               // 19
                "#{ -.4 <= 5l }",               // 20
                "#{ .123f <= -5 }",             // 21
                "#{ 12L <= 11 }",               // 22
                "#{ 11 <= 11L }"                // 23
        )

        expect:
        results[0] instanceof Boolean && results[0] == false
        results[1] instanceof Boolean && results[1] == true
        results[2] instanceof Boolean && results[2] == true

        results[3] instanceof Boolean && results[3] == false
        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == false
        results[6] instanceof Boolean && results[6] == true

        results[7] instanceof Boolean && results[7] == false
        results[8] instanceof Boolean && results[8] == true
        results[9] instanceof Boolean && results[9] == false
        results[10] instanceof Boolean && results[10] == true
        results[11] instanceof Boolean && results[11] == false

        results[12] instanceof Boolean && results[12] == false
        results[13] instanceof Boolean && results[13] == true
        results[14] instanceof Boolean && results[14] == false
        results[15] instanceof Boolean && results[15] == true
        results[16] instanceof Boolean && results[16] == false

        results[17] instanceof Boolean && results[17] == false
        results[18] instanceof Boolean && results[18] == true
        results[19] instanceof Boolean && results[19] == false
        results[20] instanceof Boolean && results[20] == true
        results[21] instanceof Boolean && results[21] == false
        results[22] instanceof Boolean && results[22] == false
        results[23] instanceof Boolean && results[23] == true
    }

    void "test comparables"() {
        given:
        Object[] results = evaluateMultipleAgainstContext("""

            record NonComparable(int value) {}

            class Comp1 implements Comparable<Comp1> {

                private final int value;

                public Comp1(int value) {
                    this.value = value;
                }

                @Override
                public int compareTo(Comp1 o) {
                    return value - o.value;
                }
            }

            class InheritedComp extends Comp1 {
                public InheritedComp(int value) {
                    super(value);
                }
            }

            class Comp2 implements Comparable<NonComparable> {
                private final int value;

                public Comp2(int value) {
                    this.value = value;
                }

                @Override
                public int compareTo(NonComparable o) {
                    return value - o.value();
                }

            }

            class Comp3 implements Comparable<Integer> {

                private final int value;

                public Comp3(int value) {
                    this.value = value;
                }

                @Override
                public int compareTo(Integer i) {
                    return value - i;
                }

            }

            class Comp4 implements Comparable {

                private final int value;

                public Comp4(int value) {
                    this.value = value;
                }

                @Override
                public int compareTo(Object i) {
                    return value - (Integer) i;
                }
            }

            @jakarta.inject.Singleton
            class Context {

                public NonComparable nonComp(int value) {
                    return new NonComparable(value);
                }

                public InheritedComp inheritedComp(int value) {
                    return new InheritedComp(value);
                }

                public Comparable<Comp1> compInterface(int value) {
                    return new Comp1(value);
                }

                public Comp1 comp1(int value) {
                    return new Comp1(value);
                }

                public Comp2 comp2(int value) {
                    return new Comp2(value);
                }

                public Comp3 comp3(int value) {
                    return new Comp3(value);
                }

                public Comp4 comp4(int value) {
                    return new Comp4(value);
                }
            }

        """,
                // comparable to itself
                "#{ comp1(10) > comp1(7) }", // 0
                "#{ comp1(10) < comp1(7) }",            // 1
                "#{ comp1(10) <= comp1(7) }",           // 2
                "#{ comp1(7) >= comp1(7) }",            // 3
                "#{ comp1(7) < comp1(8) }",             // 4
                "#{ comp1(7) <= comp1(8) }",            // 5
                "#{ comp1(7) > comp1(8) }",             // 6
                "#{ comp1(7) >= comp1(8) }",            // 7

                // left to right comparable
                "#{ comp2(10) > nonComp(7) }",          // 8
                "#{ comp2(10) < nonComp(7) }",          // 9
                "#{ comp2(10) <= nonComp(7) }",         // 10
                "#{ comp2(7) >= nonComp(7) }",          // 11
                "#{ comp2(7) < nonComp(8) }",           // 12
                "#{ comp2(7) <= nonComp(8) }",          // 13
                "#{ comp2(7) > nonComp(8) }",           // 14
                "#{ comp2(7) >= nonComp(8) }",          // 15

                // right to left comparable
                "#{ nonComp(10) > comp2(7) }",          // 16
                "#{ nonComp(10) < comp2(7) }",          // 17
                "#{ nonComp(10) <= comp2(7) }",         // 18
                "#{ nonComp(7) >= comp2(7) }",          // 19
                "#{ nonComp(7) < comp2(8) }",           // 20
                "#{ nonComp(7) <= comp2(8) }",          // 21
                "#{ nonComp(7) > comp2(8) }",           // 22
                "#{ nonComp(7) >= comp2(8) }",          // 23

                // comparable to primitive
                "#{ comp3(10) > 7 }",                   // 24
                "#{ comp3(10) < 7 }",                   // 25
                "#{ comp3(10) <= 7 }",                  // 26
                "#{ comp3(7) >= 7 }",                   // 27
                "#{ comp3(7) < 8 }",                    // 28
                "#{ comp3(7) <= 8 }",                   // 29
                "#{ comp3(7) > 8 }",                    // 30
                "#{ comp3(7) >= 8 }",                   // 31

                // primitive to comparable
                "#{ 10 > comp3(7) }",                   // 32
                "#{ 10 < comp3(7) }",                   // 33
                "#{ 10 <= comp3(7) }",                  // 34
                "#{ 7 >= comp3(7) }",                   // 35
                "#{ 7 < comp3(8) }",                    // 36
                "#{ 7 <= comp3(8) }",                   // 37
                "#{ 7 > comp3(8) }",                    // 38
                "#{ 7 >= comp3(8) }",                   // 39

                // inherited comparable
                "#{ comp1(10) > inheritedComp(7) }",    // 40
                "#{ comp1(10) < inheritedComp(7) }",    // 41
                "#{ inheritedComp(10) > comp1(7) }",    // 42
                "#{ inheritedComp(10) < comp1(7) }",    // 43

                // interface comparable
                "#{ compInterface(10) < comp1(7) }",    // 44
                "#{ compInterface(10) > comp1(7) }",    // 45
                "#{ comp1(10) < compInterface(7) }",    // 46
                "#{ comp1(10) > compInterface(7) }",    // 47

                // raw comparable
                "#{ comp4(10) > 7 }",                   // 48
                "#{ 7 > comp4(10) }"                    // 49
        )

        expect:
        // comparable to itself
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == false
        results[2] instanceof Boolean && results[2] == false
        results[3] instanceof Boolean && results[3] == true
        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == true
        results[6] instanceof Boolean && results[6] == false
        results[7] instanceof Boolean && results[7] == false

        // left to right comparable
        results[8] instanceof Boolean && results[8] == true
        results[9] instanceof Boolean && results[9] == false
        results[10] instanceof Boolean && results[10] == false
        results[11] instanceof Boolean && results[11] == true
        results[12] instanceof Boolean && results[12] == true
        results[13] instanceof Boolean && results[13] == true
        results[14] instanceof Boolean && results[14] == false
        results[15] instanceof Boolean && results[15] == false

        // right to left comparable
        results[16] instanceof Boolean && results[16] == true
        results[17] instanceof Boolean && results[17] == false
        results[18] instanceof Boolean && results[18] == false
        results[19] instanceof Boolean && results[19] == true
        results[20] instanceof Boolean && results[20] == true
        results[21] instanceof Boolean && results[21] == true
        results[22] instanceof Boolean && results[22] == false
        results[23] instanceof Boolean && results[23] == false

        // comparable to primitive
        results[24] instanceof Boolean && results[24] == true
        results[25] instanceof Boolean && results[25] == false
        results[26] instanceof Boolean && results[26] == false
        results[27] instanceof Boolean && results[27] == true
        results[28] instanceof Boolean && results[28] == true
        results[29] instanceof Boolean && results[29] == true
        results[30] instanceof Boolean && results[30] == false
        results[31] instanceof Boolean && results[31] == false

        // primitive to comparable
        results[32] instanceof Boolean && results[32] == true
        results[33] instanceof Boolean && results[33] == false
        results[34] instanceof Boolean && results[34] == false
        results[35] instanceof Boolean && results[35] == true
        results[36] instanceof Boolean && results[36] == true
        results[37] instanceof Boolean && results[37] == true
        results[38] instanceof Boolean && results[38] == false
        results[39] instanceof Boolean && results[39] == false

        // inherited comparable
        results[40] instanceof Boolean && results[40] == true
        results[41] instanceof Boolean && results[41] == false
        results[42] instanceof Boolean && results[42] == true
        results[43] instanceof Boolean && results[43] == false

        // comparable interface
        results[44] instanceof Boolean && results[44] == false
        results[45] instanceof Boolean && results[45] == true
        results[46] instanceof Boolean && results[46] == false
        results[47] instanceof Boolean && results[47] == true

        // raw comparable
        results[48] instanceof Boolean && results[48] == true
        results[49] instanceof Boolean && results[49] == false
    }

    void "test '==' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 == 10}", // 0
                "#{ 8 == 11 }",             // 1
                "#{ -3 == 3}",              // 2
                "#{ -15 == -15 }",          // 3

                // long
                "#{ 10L == 10L}",           // 4
                "#{ 8L == 11L }",           // 5
                "#{ -3L == 3L }",           // 6
                "#{ -15L == -15L }",        // 7

                // float
                "#{ 1f == 1f}",             // 8
                "#{ 0f == 1f }",            // 9

                // double
                "#{ 1d == 1.0 }",           // 10
                "#{ .0 == 1d }",            // 11

                // string
                "#{ 'str' == 'str' }",      // 12
                "#{ 'str1' == 'str2' }"     // 13
        )


        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == false
        results[2] instanceof Boolean && results[2] == false
        results[3] instanceof Boolean && results[3] == true

        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == false
        results[6] instanceof Boolean && results[6] == false
        results[7] instanceof Boolean && results[7] == true

        results[8] instanceof Boolean && results[8] == true
        results[9] instanceof Boolean && results[9] == false

        results[10] instanceof Boolean && results[10] == true
        results[11] instanceof Boolean && results[11] == false

        results[12] instanceof Boolean && results[12] == true
        results[13] instanceof Boolean && results[13] == false
    }

    void "test '!=' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                // int
                "#{ 10 != 10}",   // 0
                "#{ 8 != 11 }",             // 1
                "#{ -3 != 3}",              // 2
                "#{ -15 != -15 }",          // 3

                // long
                "#{ 10L != 10L}",           // 4
                "#{ 8L != 11L }",           // 5
                "#{ -3L != 3L }",           // 6
                "#{ -15L != -15L }",        // 7

                // float
                "#{ 1f != 1f}",             // 8
                "#{ 0f != 1f }",            // 9

                // double
                "#{ 1d != 1.0 }",           // 10
                "#{ .0 != 1d }",            // 11

                // string
                "#{ 'str' != 'str' }",      // 12
                "#{ 'str1' != 'str2' }"     // 13
        )

        expect:
        results[0] instanceof Boolean && results[0] == false
        results[1] instanceof Boolean && results[1] == true
        results[2] instanceof Boolean && results[2] == true
        results[3] instanceof Boolean && results[3] == false

        results[4] instanceof Boolean && results[4] == false
        results[5] instanceof Boolean && results[5] == true
        results[6] instanceof Boolean && results[6] == true
        results[7] instanceof Boolean && results[7] == false

        results[8] instanceof Boolean && results[8] == false
        results[9] instanceof Boolean && results[9] == true

        results[10] instanceof Boolean && results[10] == false
        results[11] instanceof Boolean && results[11] == true

        results[12] instanceof Boolean && results[12] == false
        results[13] instanceof Boolean && results[13] == true
    }

    // '^' operator in expressions means power operation
    void "test '^' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ 2^3 }", // 0
                "#{ 3L ^ 2}",           // 1
                "#{ 2.0^0}",            // 2
                "#{ 2f ^ 2L}",          // 3
                "#{ 2^2 ^2}",           // 4
                "#{ (2 ^ 32)^2}"        // 5
        )

        expect:
        results[0] instanceof Long && results[0] == 8
        results[1] instanceof Long && results[1] == 9
        results[2] instanceof Double && results[2] == 1.0
        results[3] instanceof Double && results[3] == 4.0
        results[4] instanceof Long && results[4] == 16
        results[5] instanceof Long && results[5] == 9223372036854775807L
    }

    void "test '&&' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ true && true }",     // 0
                "#{ true && false }",               // 1
                "#{ false && true }",               // 2
                "#{ false && false }",              // 3
                "#{ true and true }",               // 4
                "#{ true and false }",              // 5
                "#{ false and true }",              // 6
                "#{ false and false }",             // 7
                "#{ true and true && true  }",      // 8
                "#{ true && true and false  }"      // 9
        )

        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == false
        results[2] instanceof Boolean && results[2] == false
        results[3] instanceof Boolean && results[3] == false
        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == false
        results[6] instanceof Boolean && results[6] == false
        results[7] instanceof Boolean && results[7] == false
        results[8] instanceof Boolean && results[8] == true
        results[9] instanceof Boolean && results[9] == false
    }

    void "test '||' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ true || true }",            // 0
                "#{ true || false }",                       // 1
                "#{ false || true }",                       // 2
                "#{ false || false }",                      // 3
                "#{ true or true }",                        // 4
                "#{ true or false }",                       // 5
                "#{ false or true }",                       // 6
                "#{ false or false }",                      // 7
                "#{ true or true || true  }",               // 8
                "#{ true or true or false  }",              // 9
                "#{ true || false or false  }",             // 10
                "#{ false or false || false or true  }"     // 11
        )

        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == true
        results[2] instanceof Boolean && results[2] == true
        results[3] instanceof Boolean && results[3] == false
        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == true
        results[6] instanceof Boolean && results[6] == true
        results[7] instanceof Boolean && results[7] == false
        results[8] instanceof Boolean && results[8] == true
        results[9] instanceof Boolean && results[9] == true
        results[10] instanceof Boolean && results[10] == true
        results[11] instanceof Boolean && results[11] == true
    }

    void "test 'instanceof' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ 1 instanceof T(java.lang.Integer) }",   // 0
                "#{ 1 instanceof T(Integer) }",                         // 1
                "#{ 1L instanceof T(java.lang.Long) }",                 // 2
                "#{ 1f instanceof T(java.lang.Float) }",                // 3
                "#{ 1d instanceof T(java.lang.Double) }",               // 4
                "#{ 'str' instanceof T(java.lang.String) }",            // 5
                "#{ 'str' instanceof T(Double) }",                      // 6
                "#{ 1L instanceof T(Integer) }",                        // 7
                "#{ 1f instanceof T(Double) }"                          // 8
        )


        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == true
        results[2] instanceof Boolean && results[2] == true
        results[3] instanceof Boolean && results[3] == true
        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == true
        results[6] instanceof Boolean && results[6] == false
        results[7] instanceof Boolean && results[7] == false
        results[8] instanceof Boolean && results[8] == false
    }

    void "test 'matches' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ '123' matches '\\\\d+' }",        // 0
                "#{ '5.0' matches '[0-9]*\\\\.[0-9]+(d|D)?' }",  // 1
                "#{ '5.0' matches '[0-9]*\\\\.[0-9]+(d|D)' }",   // 2
                "#{ 'AbC' matches '[A-Za-z]*' }",                // 3
                "#{ ' ' matches '\\\\s*' }",                     // 4
                "#{ '' matches '\\\\s+' }"                       // 5
        )

        expect:
        results[0] instanceof Boolean && results[0] == true
        results[1] instanceof Boolean && results[1] == true
        results[2] instanceof Boolean && results[2] == false
        results[3] instanceof Boolean && results[3] == true
        results[4] instanceof Boolean && results[4] == true
        results[5] instanceof Boolean && results[5] == false
    }

    void "test 'empty' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ empty '' }",    // 0
                "#{ not empty '' }",              // 1
                "#{ empty null }",              // 2
                "#{ not empty null }",             // 3
                "#{ empty 'foo' }",            // 4
                "#{ not empty 'foo' }"              // 5
        )

        expect:
        results[0] == true
        results[1] == false
        results[2] == true
        results[3] == false
        results[4] == false
        results[5] == true
    }

    void "test '!' operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ !true }",    // 0
                "#{ !false }",              // 1
                "#{ !!true }",              // 2
                "#{ !!false }",             // 3
                "#{ !!!false }",            // 4
                "#{ !!!true }"              // 5
        )

        expect:
        results[0] == false
        results[1] == true
        results[2] == true
        results[3] == false
        results[4] == true
        results[5] == false
    }

    void "test unary '-'"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ -5 }",
                "#{ -(-3) }",
                "#{ -3L }",
                "#{ -1.0D }"
        )

        expect:
        results[0] instanceof Integer && results[0] == -5
        results[1] instanceof Integer && results[1] == 3
        results[2] instanceof Long && results[2] == -3
        results[3] instanceof Double && results[3] == -1.0

        when:
        evaluate("#{ --5 }")

        then:
        thrown(Throwable.class)
    }

    void "test unary '+'"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ +5 }",
                "#{ +(+3) }",
                "#{ +3L }",
                "#{ +1.0D }"
        )

        expect:
        results[0] instanceof Integer && results[0] == 5
        results[1] instanceof Integer && results[1] == 3
        results[2] instanceof Long && results[2] == 3
        results[3] instanceof Double && results[3] == 1.0

        when:
        evaluate("#{ ++5 }")

        then:
        thrown(Throwable.class)
    }

    void "test parenthesized expressions"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ (2 + 3) * 5 }",      // 0
                "#{ 2 * ( 3 + 1) }",               // 1
                "#{ (-1 + 9) * (2 + 3) }",         // 2
                "#{ 2 ^ ( 2 + 2) }",               // 3
                "#{ 5 * ( 2d * (1 - 1)) }",        // 4
                "#{ 5L * ( 2d + 2f ) }"            // 5
        )

        expect:
        results[0] instanceof Integer && results[0] == 25
        results[1] instanceof Integer && results[1] == 8
        results[2] instanceof Integer && results[2] == 40
        results[3] instanceof Long && results[3] == 16
        results[4] instanceof Double && results[4] == 0
        results[5] instanceof Double && results[5] == 20.0
    }
}
