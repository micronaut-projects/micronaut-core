package io.micronaut.expressions.parser.ast.access

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

class CandidateMethodSpec extends AbstractTypeElementSpec {

    void "test a fixed arity candidate is not compared with a variable arity candidate"() {
        expect:
        buildClassElement('''
            package test;

            class Overloads {
                void fixed(String value) {
                }

                void varargs(String value, String... values) {
                }
            }
        ''') { ClassElement classElement ->
            MethodElement fixedMethod = classElement.getEnclosedElement(ElementQuery.ALL_METHODS.named('fixed')).get()
            MethodElement varargsMethod = classElement.getEnclosedElement(ElementQuery.ALL_METHODS.named('varargs')).get()
            ClassElement stringType = fixedMethod.parameters[0].type
            CandidateMethod fixed = new CandidateMethod(fixedMethod, [stringType])
            CandidateMethod varargs = new CandidateMethod(varargsMethod, [stringType])

            assert fixed.isMatching()
            assert !fixed.isVarArgs()
            assert varargs.isMatching()
            assert varargs.isVarArgs()
            // the variable arity candidate has more parameters than the fixed arity candidate
            assert !fixed.isMoreSpecificThan(varargs)
            assert !varargs.isMoreSpecificThan(fixed)
            assert CandidateMethod.selectMostSpecific([varargs, fixed]).get() == fixed
            true
        }
    }
}
