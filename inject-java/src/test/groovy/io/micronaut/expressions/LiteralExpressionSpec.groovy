package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class LiteralExpressionSpec extends AbstractEvaluatedExpressionsSpec {

    void "test literals"() {
        given:
        List<Object> results = evaluateMultiple(
                // null
                "#{ null }",    // 0

                // string literals
                "#{ 'string literal' }",  // 1
                "#{ 'testValue' }",       // 2

                // bool literals
                "#{ false }",             // 3
                "#{ true }",              // 4

                // int literals
                "#{ 15 }",                // 5
                "#{ 0XAB013 }",           // 6
                "#{ 0xFF }",              // 7
                "#{ 291231 }",            // 8
                "#{ 0 }",                 // 9
                "#{ 0x0 }",               // 10
                "#{ 00 }",                // 11

                // long literals
                "#{ 0xFFL }",             // 12
                "#{ 0x0123l }",           // 13
                "#{ 102L }",              // 14
                "#{ 99l }",               // 15
                "#{ 0L }",                // 16

                // float literals
                "#{ 123.e+14f }",         // 17
                "#{ 123.f }",             // 18
                "#{ 123.F }",             // 19
                "#{ .123f }",             // 20
                "#{ 19F }",               // 21

                // double literals
                "#{ 123. }",              // 22
                "#{ 123.321 }",           // 23
                "#{ 123.d }",             // 24
                "#{ 123.D }",             // 25
                "#{ .123 }",              // 26
                "#{ 123D }",              // 27
                "#{ 1E-7 }",              // 28
                "#{ 1E+1d }",             // 29
                "#{ 2e-1 }",              // 30
        )

        expect:
        results[0] == null

        results[1] instanceof String && results[1] == 'string literal'
        results[2] instanceof String && results[2] == 'testValue'

        results[3] instanceof Boolean && results[3] == false
        results[4] instanceof Boolean && results[4] == true

        results[5] instanceof Integer && results[5] == 15
        results[6] instanceof Integer && results[6] == 0XAB013
        results[7] instanceof Integer && results[7] == 0xFF
        results[8] instanceof Integer && results[8] == 291231
        results[9] instanceof Integer && results[9] == 0
        results[10] instanceof Integer && results[10] == 0x0
        results[11] instanceof Integer && results[11] == 00

        results[12] instanceof Long && results[12] == 0xFFL
        results[13] instanceof Long && results[13] == 0x0123l
        results[14] instanceof Long && results[14] == 102L
        results[15] instanceof Long && results[15] == 99L
        results[16] instanceof Long && results[16] == 0L

        results[17] instanceof Float
        results[18] instanceof Float
        results[19] instanceof Float
        results[20] instanceof Float && results[20] == .123f
        results[21] instanceof Float && results[21] == 19F

        results[22] instanceof Double && results[22] == Double.valueOf("123.")
        results[23] instanceof Double && results[23] == 123.321
        results[24] instanceof Double && results[24] == Double.valueOf("123.d")
        results[25] instanceof Double && results[25] == Double.valueOf("123.D")
        results[26] instanceof Double && results[26] == .123
        results[27] instanceof Double && results[27] == 123D
        results[28] instanceof Double && results[28] == 1E-7
        results[29] instanceof Double && results[29] == 1E+1d
        results[30] instanceof Double && results[30] == 2e-1
    }
}
