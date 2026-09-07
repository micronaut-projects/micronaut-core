package io.micronaut.inject.writer

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.DefaultValueProvidingParameterElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.sourcegen.model.ClassTypeDef
import io.micronaut.sourcegen.model.ExpressionDef
import io.micronaut.sourcegen.model.MethodDef
import io.micronaut.sourcegen.model.StatementDef
import io.micronaut.sourcegen.model.TypeDef
import spock.lang.Specification

/**
 * Tests the language-neutral, caller-side default value path of {@link MethodGenUtils}, which
 * supports languages such as Scala that compile a default argument to an accessor the caller
 * invokes, rather than to Kotlin's bitmask plus {@code $default} overload.
 */
class MethodGenUtilsCallerSideDefaultsSpec extends Specification {

    /** Mirrors Scala's {@code $lessinit$greater$default$2()} static accessor. */
    private static final MethodDef SCALA_DEFAULT_GETTER = MethodDef.builder('$lessinit$greater$default$2')
            .returns(TypeDef.STRING)
            .build()

    private static ExpressionDef defaultGetterCall() {
        ClassTypeDef.of(Greeter).invokeStatic(SCALA_DEFAULT_GETTER)
    }

    private static MethodElement constructorOf(ParameterElement... parameters) {
        MethodElement.of(
                ClassElement.of(Greeter),
                AnnotationMetadata.EMPTY_METADATA,
                ClassElement.of(void.class),
                ClassElement.of(void.class),
                '<init>',
                parameters
        )
    }

    void "a parameter supplying a caller-side default is selected when no value is present"() {
        given:
        def constructor = constructorOf(
                ParameterElement.of(ClassElement.of(String), 'name'),
                new DefaultedParameter(ParameterElement.of(ClassElement.of(String), 'greeting'))
        )
        def values = [ExpressionDef.nullValue(), ExpressionDef.nullValue()]
        def hasValues = [ExpressionDef.trueValue(), ExpressionDef.falseValue()]

        when:
        def expression = MethodGenUtils.invokeBeanConstructor(
                constructor, false, true, values, hasValues, [] as List<StatementDef>)

        then: 'a plain constructor call is emitted - no defaults mask and no marker argument'
        expression instanceof ExpressionDef.NewInstance
        def newInstance = expression as ExpressionDef.NewInstance
        newInstance.values().size() == 2

        and: 'the parameter without a default is passed straight through'
        newInstance.values()[0] == values[0]

        and: 'the defaulted parameter selects between the value and the language supplied default'
        def defaulted = newInstance.values()[1]
        defaulted instanceof ExpressionDef.IfElse
        (defaulted as ExpressionDef.IfElse).ifExpression() == values[1]
        (defaulted as ExpressionDef.IfElse).elseExpression() == defaultGetterCall()
    }

    void "the default is used directly when no values are supplied at all"() {
        given:
        def constructor = constructorOf(
                new DefaultedParameter(ParameterElement.of(ClassElement.of(String), 'greeting'))
        )

        when:
        def expression = MethodGenUtils.invokeBeanConstructor(
                constructor, false, true, null, null, [] as List<StatementDef>)

        then:
        expression instanceof ExpressionDef.NewInstance
        (expression as ExpressionDef.NewInstance).values() == [defaultGetterCall()]
    }

    void "a parameter that reports a default but supplies no expression falls back to the value"() {
        given: 'a language that evaluates defaults in the callee, e.g. Python via its generated stub'
        def constructor = constructorOf(
                new OptionalParameter(ParameterElement.of(ClassElement.of(String), 'greeting'))
        )
        def values = [ExpressionDef.nullValue()]

        when:
        def expression = MethodGenUtils.invokeBeanConstructor(
                constructor, false, true, values, [ExpressionDef.falseValue()], [] as List<StatementDef>)

        then: 'nothing is generated for the default - the value is passed through unchanged'
        expression instanceof ExpressionDef.NewInstance
        (expression as ExpressionDef.NewInstance).values() == values
    }

    void "hasDefaultsParameters reports caller-side defaults as well as Kotlin defaults"() {
        given:
        def plain = ParameterElement.of(ClassElement.of(String), 'name')
        def callerSide = new DefaultedParameter(ParameterElement.of(ClassElement.of(String), 'greeting'))
        def optionalOnly = new OptionalParameter(ParameterElement.of(ClassElement.of(String), 'greeting'))

        expect:
        !MethodGenUtils.hasDefaultsParameters([plain])
        MethodGenUtils.hasDefaultsParameters([plain, callerSide])
        MethodGenUtils.hasCallerSideDefaultsParameters([callerSide])

        and: 'a parameter that is merely optional supplies no caller-side default'
        !MethodGenUtils.hasCallerSideDefaultsParameters([optionalOnly])
        !MethodGenUtils.hasDefaultsParameters([optionalOnly])

        and: 'and the Kotlin specific check is unaffected by either'
        !MethodGenUtils.hasKotlinDefaultsParameters([callerSide, optionalOnly])
    }

    /** A parameter whose default value can be materialised at the call site, as in Scala. */
    private static class DefaultedParameter implements DefaultValueProvidingParameterElement {
        @Delegate
        final ParameterElement delegate

        DefaultedParameter(ParameterElement delegate) {
            this.delegate = delegate
        }

        @Override
        boolean hasDefault() {
            true
        }

        @Override
        Optional<ExpressionDef> defaultValueExpression(ExpressionDef target) {
            Optional.of(defaultGetterCall())
        }
    }

    /** A parameter that is optional but whose default is evaluated by the callee. */
    private static class OptionalParameter implements ParameterElement {
        @Delegate
        final ParameterElement delegate

        OptionalParameter(ParameterElement delegate) {
            this.delegate = delegate
        }

        @Override
        boolean hasDefault() {
            true
        }
    }

    static class Greeter {
        Greeter(String name, String greeting) {
        }
    }
}
