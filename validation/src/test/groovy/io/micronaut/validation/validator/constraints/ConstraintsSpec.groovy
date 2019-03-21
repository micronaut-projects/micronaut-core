package io.micronaut.validation.validator.constraints

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import javax.validation.constraints.AssertFalse
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Negative
import javax.validation.constraints.NegativeOrZero
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.validation.constraints.Null
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero
import javax.validation.constraints.Size

class ConstraintsSpec extends AbstractTypeElementSpec {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()
    @Shared
    ConstraintValidatorRegistry reg = context.getBean(ConstraintValidatorRegistry)

    @Unroll
    void "test #constraint constraint for value [#value]"() {
        expect:
        reg.getConstraintValidator(constraint, value?.getClass() ?: Object)
                .isValid(value, metadata, null) == isValid

        where:
        constraint     | value                                | isValid | metadata
        AssertTrue     | null                                 | true    | AnnotationMetadata.EMPTY_METADATA
        AssertTrue     | true                                 | true    | AnnotationMetadata.EMPTY_METADATA
        AssertTrue     | false                                | false   | AnnotationMetadata.EMPTY_METADATA
        AssertFalse    | null                                 | true    | AnnotationMetadata.EMPTY_METADATA
        AssertFalse    | true                                 | false   | AnnotationMetadata.EMPTY_METADATA
        AssertFalse    | false                                | true    | AnnotationMetadata.EMPTY_METADATA
        NotNull        | null                                 | false   | AnnotationMetadata.EMPTY_METADATA
        NotNull        | ""                                   | true    | AnnotationMetadata.EMPTY_METADATA
        Null           | null                                 | true    | AnnotationMetadata.EMPTY_METADATA
        Null           | ""                                   | false   | AnnotationMetadata.EMPTY_METADATA
        NotBlank       | ""                                   | false   | AnnotationMetadata.EMPTY_METADATA
        NotBlank       | null                                 | false   | AnnotationMetadata.EMPTY_METADATA
        NotBlank       | "  "                                 | false   | AnnotationMetadata.EMPTY_METADATA
        NotBlank       | "foo"                                | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | ""                                   | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | []                                   | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | null                                 | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as String[]                       | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as int[]                          | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as int[]                         | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as short[]                       | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as short[]                        | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as byte[]                         | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as byte[]                        | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as long[]                         | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as long[]                        | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as double[]                       | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as double[]                      | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as float[]                        | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as float[]                       | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [] as char[]                         | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1] as char[]                        | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [:]                                  | false   | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [foo: 'bar']                         | true    | AnnotationMetadata.EMPTY_METADATA
        NotEmpty       | [1]                                  | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -1                                   | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -100                                 | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -100 as double                       | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -100 as long                         | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -100 as float                        | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -100 as short                        | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | -100 as byte                         | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | new BigInteger("-100")               | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | new BigDecimal("-100")               | true    | AnnotationMetadata.EMPTY_METADATA
        Negative       | new BigInteger("100")                | false   | AnnotationMetadata.EMPTY_METADATA
        Negative       | new BigDecimal("100")                | false   | AnnotationMetadata.EMPTY_METADATA
        Negative       | 0                                    | false   | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -1                                   | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -100                                 | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -100 as double                       | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -100 as long                         | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -100 as float                        | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -100 as short                        | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | -100 as byte                         | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | new BigInteger("-100")               | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | new BigDecimal("-100")               | true    | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | new BigInteger("100")                | false   | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | new BigDecimal("100")                | false   | AnnotationMetadata.EMPTY_METADATA
        NegativeOrZero | 0                                    | true    | AnnotationMetadata.EMPTY_METADATA
        // Positive
        Positive       | 1                                    | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | 100                                  | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | 100 as double                        | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | 100 as long                          | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | 100 as float                         | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | 100 as short                         | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | 100 as byte                          | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | new BigInteger("100")                | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | new BigDecimal("100")                | true    | AnnotationMetadata.EMPTY_METADATA
        Positive       | new BigInteger("-100")               | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | new BigDecimal("-100")               | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | 0                                    | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | -100 as double                       | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | -100 as long                         | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | -100 as float                        | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | -100 as short                        | false   | AnnotationMetadata.EMPTY_METADATA
        Positive       | -100 as byte                         | false   | AnnotationMetadata.EMPTY_METADATA
        // PositiveOrZero
        PositiveOrZero | 1                                    | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | null                                 | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 0                                    | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | -1                                   | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 100                                  | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 100 as double                        | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 100 as long                          | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 100 as float                         | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 100 as short                         | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | 100 as byte                          | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | new BigInteger("100")                | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | new BigDecimal("100")                | true    | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | new BigInteger("-100")               | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | new BigDecimal("-100")               | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | -100 as double                       | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | -100 as long                         | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | -100 as float                        | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | -100 as short                        | false   | AnnotationMetadata.EMPTY_METADATA
        PositiveOrZero | -100 as byte                         | false   | AnnotationMetadata.EMPTY_METADATA
        // Max
        Max            | 10                                   | false   | constraintMetadata("@Max(5)")
        Max            | 5                                    | false   | constraintMetadata("@Max(5)")
        Max            | new BigInteger("10")                 | false   | constraintMetadata("@Max(5)")
        Max            | 5                                    | false   | constraintMetadata("@Max(5)")
        Max            | 0                                    | true    | constraintMetadata("@Max(5)")
        Max            | null                                 | true    | constraintMetadata("@Max(5)")
        // Min
        Min            | 10                                   | true    | constraintMetadata("@Min(5)")
        Min            | 5                                    | true    | constraintMetadata("@Min(5)")
        Min            | new BigInteger("10")                 | true    | constraintMetadata("@Min(5)")
        Min            | 5                                    | true    | constraintMetadata("@Min(5)")
        Min            | 0                                    | false   | constraintMetadata("@Min(5)")
        Min            | null                                 | true    | constraintMetadata("@Max(5)")
        // Size
        Size           | null                                 | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | "test"                               | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | "t"                                  | false   | constraintMetadata("@Size(min=2, max=5)")
        Size           | "te"                                 | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | "test1"                              | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | "test12"                             | false   | constraintMetadata("@Size(min=2, max=5)")
        // Size Collection
        Size           | [1, 2, 3]                            | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | [1]                                  | false   | constraintMetadata("@Size(min=2, max=5)")
        Size           | [1, 2]                               | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | [1, 2, 3, 4, 5]                      | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | [1, 2, 3, 4, 5, 6]                   | false   | constraintMetadata("@Size(min=2, max=5)")
        // Size Map
        Size           | [a: 1, b: 2, c: 3]                   | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | [a: 1]                               | false   | constraintMetadata("@Size(min=2, max=5)")
        Size           | [a: 1, b: 2]                         | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | [a: 1, b: 2, c: 3, d: 4, e: 5]       | true    | constraintMetadata("@Size(min=2, max=5)")
        Size           | [a: 1, b: 2, c: 3, d: 4, e: 5, f: 6] | false   | constraintMetadata("@Size(min=2, max=5)")
        // DecimalMax
        DecimalMax     | null                                 | true    | constraintMetadata("@DecimalMax(\"5\")")
        DecimalMax     | 10                                   | false   | constraintMetadata("@DecimalMax(\"5\")")
        DecimalMax     | new BigDecimal("10")                 | false   | constraintMetadata("@DecimalMax(\"5\")")
        DecimalMax     | 5                                    | true    | constraintMetadata("@DecimalMax(\"5\")")
        DecimalMax     | "10"                                 | false   | constraintMetadata("@DecimalMax(\"5\")")
        DecimalMax     | "5"                                  | true    | constraintMetadata("@DecimalMax(\"5\")")
        DecimalMax     | "5"                                  | false   | constraintMetadata("@DecimalMax(value=\"5\",inclusive=false)")
        // DecimalMin
        DecimalMin     | null                                 | true    | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | 10                                   | true    | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | 3                                    | false   | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | new BigDecimal("10")                 | true    | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | 5                                    | true    | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | "10"                                 | true    | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | "3"                                  | false   | constraintMetadata("@DecimalMin(\"5\")")
        DecimalMin     | "5"                                  | false   | constraintMetadata("@DecimalMin(value=\"5\",inclusive=false)")

    }

    private AnnotationMetadata constraintMetadata(String ann) {
        buildAnnotationMetadata(ann, "javax.validation.constraints")
    }
}
