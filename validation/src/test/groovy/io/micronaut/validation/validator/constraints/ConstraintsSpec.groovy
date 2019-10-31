package io.micronaut.validation.validator.constraints

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.DefaultClockProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import javax.validation.constraints.AssertFalse
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.Email
import javax.validation.constraints.Future
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Negative
import javax.validation.constraints.NegativeOrZero
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.validation.constraints.Null
import javax.validation.constraints.Past
import javax.validation.constraints.Pattern
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero
import javax.validation.constraints.Size
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import static java.math.BigInteger.ONE

class ConstraintsSpec extends AbstractTypeElementSpec {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()
    @Shared
    ConstraintValidatorRegistry reg = context.getBean(ConstraintValidatorRegistry)

    @Unroll
    void "test #constraint constraint for value [#value]"() {
        given:
        def context = Mock(ConstraintValidatorContext)
        context.getClockProvider() >> new DefaultClockProvider()
        def validator = reg.getConstraintValidator(constraint, value?.getClass() ?: Object)

        expect:
        validator.isValid(value, metadata, context) == isValid

        where:
        constraint     | value                                         | isValid | metadata
        AssertTrue     | null                                          | true    | new AnnotationValue<>(constraint.getName())
        AssertTrue     | true                                          | true    | new AnnotationValue<>(constraint.getName())
        AssertTrue     | false                                         | false   | new AnnotationValue<>(constraint.getName())
        AssertFalse    | null                                          | true    | new AnnotationValue<>(constraint.getName())
        AssertFalse    | true                                          | false   | new AnnotationValue<>(constraint.getName())
        AssertFalse    | false                                         | true    | new AnnotationValue<>(constraint.getName())
        NotNull        | null                                          | false   | new AnnotationValue<>(constraint.getName())
        NotNull        | ""                                            | true    | new AnnotationValue<>(constraint.getName())
        Null           | null                                          | true    | new AnnotationValue<>(constraint.getName())
        Null           | ""                                            | false   | new AnnotationValue<>(constraint.getName())
        NotBlank       | ""                                            | false   | new AnnotationValue<>(constraint.getName())
        NotBlank       | null                                          | false   | new AnnotationValue<>(constraint.getName())
        NotBlank       | "  "                                          | false   | new AnnotationValue<>(constraint.getName())
        NotBlank       | "foo"                                         | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | ""                                            | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | []                                            | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | null                                          | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as String[]                                | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as int[]                                   | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as int[]                                  | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as short[]                                | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as short[]                                 | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as byte[]                                  | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as byte[]                                 | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as long[]                                  | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as long[]                                 | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as double[]                                | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as double[]                               | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as float[]                                 | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as float[]                                | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [] as char[]                                  | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1] as char[]                                 | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [:]                                           | false   | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [foo: 'bar']                                  | true    | new AnnotationValue<>(constraint.getName())
        NotEmpty       | [1]                                           | true    | new AnnotationValue<>(constraint.getName())
        Negative       | -1                                            | true    | new AnnotationValue<>(constraint.getName())
        Negative       | -100                                          | true    | new AnnotationValue<>(constraint.getName())
        Negative       | -100 as double                                | true    | new AnnotationValue<>(constraint.getName())
        Negative       | -0.001 as double                              | true    | new AnnotationValue<>(constraint.getName())
        Negative       | -100 as long                                  | true    | new AnnotationValue<>(constraint.getName())
        Negative       | Integer.MIN_VALUE - 1L as long                | true    | new AnnotationValue<>(constraint.getName())
        Negative       | Integer.MAX_VALUE + 1L as long                | false   | new AnnotationValue<>(constraint.getName())
        Negative       | -100 as float                                    | true  | new AnnotationValue<>(constraint.getName())
        Negative       | -100 as short                                    | true  | new AnnotationValue<>(constraint.getName())
        Negative       | -100 as byte                                     | true  | new AnnotationValue<>(constraint.getName())
        Negative       | new BigInteger("-100")                           | true  | new AnnotationValue<>(constraint.getName())
        Negative       | BigInteger.valueOf(Long.MIN_VALUE).subtract(ONE)     | true  | new AnnotationValue<>(constraint.getName())
        Negative       | new BigDecimal("-100")                           | true  | new AnnotationValue<>(constraint.getName())
        Negative       | new BigDecimal("-0.01")                          | true  | new AnnotationValue<>(constraint.getName())
        Negative       | new BigInteger("100")                            | false | new AnnotationValue<>(constraint.getName())
        Negative       | BigInteger.valueOf(Long.MAX_VALUE).add(ONE)          | false  | new AnnotationValue<>(constraint.getName())
        Negative       | new BigDecimal("100")                            | false | new AnnotationValue<>(constraint.getName())
        Negative       | BigDecimal.ZERO                                   | false   | new AnnotationValue<>(constraint.getName())
        Negative       | 0                                             | false   | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -1                                            | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -100                                          | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -100 as double                                | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -0.001 as double                              | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero |  0.001 as double                              | false   | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -100 as long                                  | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | Integer.MIN_VALUE - 1L as long                | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | Integer.MAX_VALUE + 1L as long                | false   | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -100 as float                                 | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -100 as short                                 | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | -100 as byte                                  | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | new BigInteger("-100")                        | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | BigInteger.valueOf(Long.MIN_VALUE).subtract(ONE)  | true  | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | new BigDecimal("-100")                        | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | BigDecimal.ZERO                                   | true    | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | new BigInteger("100")                         | false   | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | BigInteger.valueOf(Long.MAX_VALUE).add(ONE)       | false  | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | new BigDecimal("100")                         | false   | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | new BigDecimal("0.01")                        | false   | new AnnotationValue<>(constraint.getName())
        NegativeOrZero | 0                                             | true    | new AnnotationValue<>(constraint.getName())
        // Positive
        Positive       | 1                                             | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 100                                           | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 100 as double                                 | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 0.001 as double                               | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 100 as long                                   | true    | new AnnotationValue<>(constraint.getName())
        Positive       | Integer.MAX_VALUE + 1L as long                | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 100 as float                                  | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 100 as short                                  | true    | new AnnotationValue<>(constraint.getName())
        Positive       | 100 as byte                                   | true    | new AnnotationValue<>(constraint.getName())
        Positive       | new BigInteger("100")                         | true    | new AnnotationValue<>(constraint.getName())
        Positive       | BigInteger.valueOf(Long.MAX_VALUE).add(ONE)       | true    | new AnnotationValue<>(constraint.getName())
        Positive       | new BigDecimal("100")                         | true    | new AnnotationValue<>(constraint.getName())
        Positive       | new BigDecimal("0.01")                        | true    | new AnnotationValue<>(constraint.getName())
        Positive       | new BigInteger("-100")                        | false   | new AnnotationValue<>(constraint.getName())
        Positive       | new BigDecimal("-100")                        | false   | new AnnotationValue<>(constraint.getName())
        Positive       | BigDecimal.ZERO                                   | false   | new AnnotationValue<>(constraint.getName())
        Positive       | 0                                                 | false   | new AnnotationValue<>(constraint.getName())
        Positive       | -100 as double                                    | false   | new AnnotationValue<>(constraint.getName())
        Positive       | -100 as long                                      | false   | new AnnotationValue<>(constraint.getName())
        Positive       | Integer.MIN_VALUE - 1L as long                    | false   | new AnnotationValue<>(constraint.getName())
        Positive       | BigInteger.valueOf(Long.MIN_VALUE).subtract(ONE)  | false  | new AnnotationValue<>(constraint.getName())
        Positive       | -100 as float                                     | false   | new AnnotationValue<>(constraint.getName())
        Positive       | -100 as short                                     | false   | new AnnotationValue<>(constraint.getName())
        Positive       | -100 as byte                                      | false   | new AnnotationValue<>(constraint.getName())
        // PositiveOrZero
        PositiveOrZero | 1                                             | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | null                                          | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 0                                             | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -1                                            | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 100                                           | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 100 as double                                 | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 100 as long                                   | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | Integer.MAX_VALUE + 1L as long                | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 100 as float                                  | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 100 as short                                  | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | 100 as byte                                   | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | new BigInteger("100")                         | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | BigInteger.valueOf(Long.MAX_VALUE).add(ONE)       | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | new BigDecimal("100")                         | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | BigDecimal.ZERO                                   | true    | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | new BigInteger("-100")                        | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | BigInteger.valueOf(Long.MIN_VALUE).subtract(ONE)  | false  | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | new BigDecimal("-100")                        | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | new BigDecimal("-0.01")                           | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -100 as double                                | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -0.001 as double                              | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -100 as long                                  | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | Integer.MIN_VALUE - 1L as long                | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -100 as float                                 | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -100 as short                                 | false   | new AnnotationValue<>(constraint.getName())
        PositiveOrZero | -100 as byte                                  | false   | new AnnotationValue<>(constraint.getName())
        // Max
        Max            | 10                                            | false   | constraintMetadata(constraint, "@Max(5)")
        Max            | 5                                             | true   | constraintMetadata(constraint, "@Max(5)")
        Max            | new BigInteger("10")                          | false   | constraintMetadata(constraint, "@Max(5)")
        Max            | new BigDecimal("10")                          | false   | constraintMetadata(constraint, "@Max(5)")
        Max            | 0                                             | true    | constraintMetadata(constraint, "@Max(5)")
        Max            | null                                          | true    | constraintMetadata(constraint, "@Max(5)")
        // Min
        Min            | 10                                            | true    | constraintMetadata(constraint, "@Min(5)")
        Min            | 5                                             | true    | constraintMetadata(constraint, "@Min(5)")
        Min            | new BigInteger("10")                          | true    | constraintMetadata(constraint, "@Min(5)")
        Min            | new BigDecimal("10")                          | true    | constraintMetadata(constraint, "@Min(5)")
        Min            | 0                                             | false   | constraintMetadata(constraint, "@Min(5)")
        Min            | null                                          | true    | constraintMetadata(constraint, "@Min(5)")
        // Size
        Size           | null                                          | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | "test"                                        | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | "t"                                           | false   | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | "te"                                          | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | "test1"                                       | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | "test12"                                      | false   | constraintMetadata(constraint, "@Size(min=2, max=5)")
        // Size Collection
        Size           | [1, 2, 3]                                     | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [1]                                           | false   | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [1, 2]                                        | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [1, 2, 3, 4, 5]                               | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [1, 2, 3, 4, 5, 6]                            | false   | constraintMetadata(constraint, "@Size(min=2, max=5)")
        // Size Map
        Size           | [a: 1, b: 2, c: 3]                            | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [a: 1]                                        | false   | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [a: 1, b: 2]                                  | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [a: 1, b: 2, c: 3, d: 4, e: 5]                | true    | constraintMetadata(constraint, "@Size(min=2, max=5)")
        Size           | [a: 1, b: 2, c: 3, d: 4, e: 5, f: 6]          | false   | constraintMetadata(constraint, "@Size(min=2, max=5)")
        // DecimalMax
        DecimalMax     | null                                          | true    | constraintMetadata(constraint, "@DecimalMax(\"5\")")
        DecimalMax     | 10                                            | false   | constraintMetadata(constraint, "@DecimalMax(\"5\")")
        DecimalMax     | new BigDecimal("10")                          | false   | constraintMetadata(constraint, "@DecimalMax(\"5\")")
        DecimalMax     | 5                                             | true    | constraintMetadata(constraint, "@DecimalMax(\"5\")")
        DecimalMax     | "10"                                          | false   | constraintMetadata(constraint, "@DecimalMax(\"5\")")
        DecimalMax     | "5"                                           | true    | constraintMetadata(constraint, "@DecimalMax(\"5\")")
        DecimalMax     | "5"                                           | false   | constraintMetadata(constraint, "@DecimalMax(value=\"5\",inclusive=false)")
        // DecimalMin
        DecimalMin     | null                                          | true    | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | 10                                            | true    | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | 3                                             | false   | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | new BigDecimal("10")                          | true    | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | 5                                             | true    | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | "10"                                          | true    | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | "3"                                           | false   | constraintMetadata(constraint, "@DecimalMin(\"5\")")
        DecimalMin     | "5"                                           | false   | constraintMetadata(constraint, "@DecimalMin(value=\"5\",inclusive=false)")
        // Pattern
        Pattern        | null                                          | true    | constraintMetadata(constraint, /@Pattern(regexp="\\d+")/)
        Pattern        | 'abc'                                         | false   | constraintMetadata(constraint, /@Pattern(regexp="\\d+")/)
        Pattern        | '123'                                         | true    | constraintMetadata(constraint, /@Pattern(regexp="\\d+")/)
        // Digits
        Digits         | null                                          | true    | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | "10.15"                                       | true    | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | "110.15"                                      | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | "10.150"                                      | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | "110.150"                                     | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 10.15                                         | true    | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 110.15                                        | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 10.150                                        | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 110.150                                       | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 10.15d                                        | true    | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 110.15d                                       | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 10.150d                                       | true    | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 110.150d                                      | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 10                                            | true    | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)
        Digits         | 110                                           | false   | constraintMetadata(constraint, /@Digits(integer=2, fraction=2)/)

        // Past
        Past           | null                                          | true    | constraintMetadata(constraint, /@Past/)
        Past           | Instant.now().minus(1, ChronoUnit.DAYS)       | true    | constraintMetadata(constraint, /@Past/)
        Past           | Instant.now().plus(1, ChronoUnit.DAYS)        | false   | constraintMetadata(constraint, /@Past/)
        Past           | LocalDateTime.now().minus(1, ChronoUnit.DAYS) | true    | constraintMetadata(constraint, /@Past/)
        Past           | LocalDateTime.now().plus(1, ChronoUnit.DAYS)  | false   | constraintMetadata(constraint, /@Past/)

        // Future
        Future         | null                                          | true    | constraintMetadata(constraint, /@Past/)
        Future         | Instant.now().minus(1, ChronoUnit.DAYS)       | false   | constraintMetadata(constraint, /@Past/)
        Future         | Instant.now().plus(1, ChronoUnit.DAYS)        | true    | constraintMetadata(constraint, /@Past/)
        Future         | LocalDateTime.now().minus(1, ChronoUnit.DAYS) | false   | constraintMetadata(constraint, /@Past/)
        Future         | LocalDateTime.now().plus(1, ChronoUnit.DAYS)  | true    | constraintMetadata(constraint, /@Past/)

        // Email
        Email          | null                                          | true    | constraintMetadata(constraint, /@Email/)
        Email          | "junk"                                        | false   | constraintMetadata(constraint, /@Email/)
        Email          | "junk@junk.com"                               | true    | constraintMetadata(constraint, /@Email/)
    }

    private AnnotationValue constraintMetadata(Class annotation, String ann) {
        buildAnnotationMetadata(ann, "javax.validation.constraints").getAnnotation(annotation)
    }
}
