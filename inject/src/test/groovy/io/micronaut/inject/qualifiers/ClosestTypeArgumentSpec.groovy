package io.micronaut.inject.qualifiers

import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.Stream

class ClosestTypeArgumentSpec extends Specification {

    void "test closes with 1 type argument"() {
        given:
        BeanDefinition a = stubFor(D)
        BeanDefinition b = stubFor(C)

        when: "looking for E"
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(E).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        then: "D is closer to E than C"
        beanDefinitions.size() == 1
        beanDefinitions[0] == a

        when: "looking for A"
        beanDefinitions = Qualifiers.byTypeArgumentsClosest(A).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        then:
        beanDefinitions.size() == 0

        when: "looking for C"
        beanDefinitions = Qualifiers.byTypeArgumentsClosest(C).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        then: "C matches"
        beanDefinitions.size() == 1
        beanDefinitions[0] == b
    }

    void "test concrete is preferred over interface"() {
        given:
        BeanDefinition a = stubFor(D)
        BeanDefinition b = stubFor(D2)

        when: "looking for G"
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(G).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        then: "D is returned"
        beanDefinitions.size() == 1
        beanDefinitions[0] == a
    }

    void "test different hierarchies"() {
        BeanDefinition a = stubFor(C)
        BeanDefinition b = stubFor(C2)

        when: "looking for F"
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(F).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        then: "C is only returned"
        beanDefinitions.size() == 1
        beanDefinitions[0] == a


        when: "looking for C2"
        beanDefinitions = Qualifiers.byTypeArgumentsClosest(C2).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        then: "C2 is only returned"
        beanDefinitions.size() == 1
        beanDefinitions[0] == b
    }

    void "test 2 type arguments equal distance"() {
        given:
        BeanDefinition a = stubFor(D, C)
        BeanDefinition b = stubFor(C, D)

        when:
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(E, E).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        /* E -> D = 1 + E -> C = 2 = 3 */
        /* E -> C = 2 + E -> D = 1 = 3 */

        then:
        beanDefinitions.size() == 2
        beanDefinitions[0] == a
        beanDefinitions[1] == b
    }

    void "test 2 type arguments"() {
        given:
        BeanDefinition a = stubFor(D, C)
        BeanDefinition b = stubFor(C, C)

        when:
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(E, C).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        /* E -> D = 1 + C -> C = 0 = 1 : A wins*/
        /* E -> C = 2 + C -> C = 0 = 2*/

        then:
        beanDefinitions.size() == 1
        beanDefinitions[0] == a
    }

    void "test 2 type arguments, one is out of bounds"() {
        given:
        BeanDefinition a = stubFor(D, A)
        BeanDefinition b = stubFor(E, C)

        when:
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(E, B).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        /* E -> D = 1 + B -> A = 1 = 2 : A wins */
        /* B -> C is disqualifying */

        then:
        beanDefinitions.size() == 1
        beanDefinitions[0] == a
    }

    void "test 3 type arguments"() {
        given:
        BeanDefinition a = stubFor(E, D, C)
        BeanDefinition b = stubFor(E, D, C2)

        when:
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(G, H, F).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        /* G -> E = 2 + H -> D = 4 + F -> C = 3 = 9 : A wins */
        /* F -> C2 = is not same hierarchy */

        then:
        beanDefinitions.size() == 1
        beanDefinitions[0] == a
    }

    void "test 3 type arguments same distance"() {
        given:
        BeanDefinition a = stubFor(E, D, C)
        BeanDefinition b = stubFor(D, C, E)

        when:
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(G, G, G).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())
        /* G -> E = 2 + G -> D = 3 + G -> C = 4 = 9 */
        /* G -> D = 3 + G -> C = 4 + G -> E = 2 = 9 */

        then:
        beanDefinitions.size() == 2
        beanDefinitions[0] == a
        beanDefinitions[1] == b
    }

    void "test 3 type arguments again"() {
        given:
        BeanDefinition a = stubFor(E, D, C)
        BeanDefinition b = stubFor(D, C, F)

        when:
        List<BeanDefinition> beanDefinitions = Qualifiers.byTypeArgumentsClosest(G, G, G).reduce(BeanType, Stream.of(a, b)).collect(Collectors.toList())

        /* G -> E = 2 + G -> D = 3 + G -> C = 4 = 9 */
        /* G -> D = 3 + G -> C = 4 + G -> F = 1 = 8 : B wins */

        then:
        beanDefinitions.size() == 1
        beanDefinitions[0] == b
    }

    private BeanDefinition stubFor(Class... typeArguments) {
        Stub(BeanDefinition) {
            getTypeArguments(BeanType) >> {
                typeArguments.collect { Argument.of(it) }
            }
            getBeanType() >> BeanType
        }
    }

    static class BeanType {}

    static class A {}
    static class B extends A {}
    static class C extends B {}
    static class D extends C {}
    static interface D2 {}
    static interface C2 extends D2 {}
    static class B2 implements C2 {}
    static class E extends D implements D2 {}
    static interface E2 {}
    static class F extends E implements E2 {}
    static class G extends F {}
    static class H extends G {}



}


