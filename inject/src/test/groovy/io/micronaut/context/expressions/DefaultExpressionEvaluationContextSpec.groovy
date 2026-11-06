package io.micronaut.context.expressions

import io.micronaut.context.exceptions.ExpressionEvaluationException
import spock.lang.Specification

class DefaultExpressionEvaluationContextSpec extends Specification {

    void "getArgument returns the argument at a valid index"() {
        given:
        def context = new DefaultExpressionEvaluationContext(null, ['first', 'second'] as Object[], null, null)

        expect:
        context.getArgument(1) == 'second'
    }

    void "getArgument rejects an invalid index or unavailable arguments"() {
        given:
        def context = new DefaultExpressionEvaluationContext(null, arguments, null, null)

        when:
        context.getArgument(index)

        then:
        thrown(ExpressionEvaluationException)

        where:
        arguments                       | index
        ['first', 'second'] as Object[] | 2
        ['first', 'second'] as Object[] | 3
        ['first', 'second'] as Object[] | -1
        null                            | 0
        [] as Object[]                  | 0
    }
}
