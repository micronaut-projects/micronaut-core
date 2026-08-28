/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.qualifiers.replaces

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import io.micronaut.context.env.PropertySource
/**
 * Created by graemerocher on 26/05/2017.
 */
class ReplacesSpec extends AbstractTypeElementSpec {

    void "test that a bean can be marked to replace another bean"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:"A bean has a dependency on an interface with multiple impls"
        B b = context.getBean(B)

        then:"The impl that replaces the other impl is the only one present"
        b.all.size() == 1
        !b.all.any() { it instanceof A1 }
        b.all.any() { it instanceof A2 }
        b.a instanceof A2

        cleanup:
        context.close()
    }

    void "test that a configuration bean can be marked to replace another bean x"() {

        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Replaces;

import java.util.HashSet;
import java.util.Set;

@ConfigurationProperties("test.props")
class A1ConfigProperties {
    @ConfigurationBuilder(prefixes = "set")
    Something builder = new Something();

    class Something {
        Set<Foo> foos = new HashSet<>();

        public Set<Foo> getFoos() {
            return foos;
        }

        public void setFoos(Set<Foo> foos) {
            this.foos = foos;
        }
    }

    public Something getBuilder() {
        builder.foos.add(Foo.AUTO_ADDED_A1);
        return builder;
    }

    enum Foo {
        BAR,
        BAZ,
        AUTO_ADDED_A1,
        AUTO_ADDED_A2
    }
}

@Replaces(A1ConfigProperties.class)
@Singleton
@ConfigurationProperties("test.props")
class A2ConfigProperties extends A1ConfigProperties {

    @Override
    public Something getBuilder() {
        Something builderParent = super.getBuilder();
        builderParent.foos.add(Foo.AUTO_ADDED_A2);
        return builderParent;
    }

}

""")
        context.getEnvironment().addPropertySource(PropertySource.of(["test.props.foos": "BAR"]))


        when:"A bean has a dependency on an interface with multiple impls"
        def a1ConfigProperties = context.getBean(context.getClassLoader().loadClass("test.A2ConfigProperties"))

        then:
        noExceptionThrown()
        // It should contain the AUTO_ADDED_A1, AUTO_ADDED_A2 and the BAR.
        // when you comment out the //@Replaces(A1ConfigProperties.class) on the A2ConfigProperties you get AUTO_ADDED_A1 and BAR.
        a1ConfigProperties.builder.foos.size() == 3
//        a1ConfigProperties instanceof A2ConfigProperties
        a1ConfigProperties.builder.foos.first() instanceof Enum

        cleanup:
        context.close()
    }

    void "test that a configuration bean can be marked to replace another bean"() {

        given:
        ApplicationContext context = ApplicationContext.run()
        context.getEnvironment().addPropertySource(PropertySource.of(["test.props.foos": "BAR"]))


        when:"A bean has a dependency on an interface with multiple impls"
        A1ConfigProperties a1ConfigProperties = context.getBean(A1ConfigProperties)

        then:
        noExceptionThrown()
        // It should contain the AUTO_ADDED_A1, AUTO_ADDED_A2 and the BAR.
        // when you comment out the //@Replaces(A1ConfigProperties.class) on the A2ConfigProperties you get AUTO_ADDED_A1 and BAR.
        a1ConfigProperties.builder.foos.size() == 3
        a1ConfigProperties instanceof A2ConfigProperties
        a1ConfigProperties.builder.foos.first() instanceof Enum

        cleanup:
        context.close()
    }

    void "test that a bean that has AOP advice applied can be replaced"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        expect:
        context.getBeansOfType(H).size() == 1
        context.getBean(H).test("foo") == "replacement foo"

        cleanup:
        context.close()
    }

    void "test that named beans can be replaced"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        expect:
        context.containsBean(E1Replacement)
        context.containsBean(E2Replacement)
        context.containsBean(E3)
        context.containsBean(E)
        context.getBeansOfType(E).size() == 3
        context.getBeansOfType(E).contains(context.getBean(E1Replacement))
        context.getBeansOfType(E).contains(context.getBean(E2Replacement))
        context.getBeansOfType(E).contains(context.getBean(E3))

        cleanup:
        context.close()
    }

    void "test that qualified beans can be replaced"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        expect:
        context.containsBean(G1QualifierReplacement)
        context.containsBean(G2QualifierReplacement)
        context.containsBean(G3Qualifier)

        context.containsBean(G)
        context.getBeansOfType(G).size() == 3
        context.getBeansOfType(G).contains(context.getBean(G1QualifierReplacement))
        context.getBeansOfType(G).contains(context.getBean(G2QualifierReplacement))
        context.getBeansOfType(G).contains(context.getBean(G3Qualifier))

        cleanup:
        context.close()
    }

    void "test that introduction advice can be replaced with inheritance"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:
        IntroductionOperations ops = ctx.getBean(IntroductionOperations)

        then:
        ops instanceof IntroductionReplacement
        ctx.getBeansOfType(IntroductionOperations).size() == 1


        cleanup:
        ctx.close()
    }

    void "test that introduction advice can be replaced"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:
        IntroductionB ops = ctx.getBean(IntroductionB)

        then:
        ops instanceof AnotherIntroductionReplacement
        ctx.getBeansOfType(IntroductionB).size() == 1


        cleanup:
        ctx.close()
    }

    void "test that classes with around advice can be replaced"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:
        AroundOps ops = ctx.getBean(AroundOps)

        then:
        ops instanceof AroundReplacement
        ctx.getBeansOfType(AroundOps).size() == 1
        ctx.getBeansOfType(AroundOps).iterator().next()  == ops

        cleanup:
        ctx.close()
    }

    void "test replacing an entire factory"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:
        C c = ctx.getBean(C)

        then:
        c instanceof C2

        cleanup:
        ctx.close()
    }

    void "test replacing a factory method"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:
        D d = ctx.getBean(D)

        then:
        d instanceof D2

        cleanup:
        ctx.close()
    }

    void "test replacing a bean with AOP bean of the same type"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        when:
        F f = ctx.getBean(F)

        then:
        f.getId() == "replaces"

        cleanup:
        ctx.close()
    }

    void "test replacing a chain of factory methods"() {
        given:
        def ctx = ApplicationContext.run(['spec.name':'ReplacesSpec'], "factory-replacement-chain")

        when:
        D d = ctx.getBean(D)

        then:
        d instanceof D3

        cleanup:
        ctx.close()
    }
}
