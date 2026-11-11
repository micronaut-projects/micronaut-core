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
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.qualifiers.Qualifiers
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

    private static final String GREETER = """
package test;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

interface Greeter {
}

@Singleton
class DefaultGreeter implements Greeter {
}

@Singleton
@Replaces(Greeter.class)
class ReplacingGreeter implements Greeter {
}
"""

    void "test a bean that replaces a bean that replaces its interface"() {
        given:
        ApplicationContext context = buildContext(GREETER + """
@Singleton
@Replaces(ReplacingGreeter.class)
class MockGreeter implements Greeter {
}
""")
        def greeter = context.classLoader.loadClass("test.Greeter")

        expect:
        context.getBean(greeter).class.simpleName == "MockGreeter"
        context.getBeansOfType(greeter)*.class*.simpleName == ["MockGreeter"]

        cleanup:
        context.close()
    }

    void "test a factory bean of the interface type that replaces a bean that replaces its interface"() {
        given:
        ApplicationContext context = buildContext(GREETER + """
@io.micronaut.context.annotation.Factory
class MockFactory {
    @Singleton
    @Replaces(ReplacingGreeter.class)
    Greeter mockGreeter() {
        return new Greeter() { };
    }
}
""")
        def greeter = context.classLoader.loadClass("test.Greeter")

        expect:
        context.getBeansOfType(greeter).size() == 1
        !["DefaultGreeter", "ReplacingGreeter"].contains(context.getBean(greeter).class.simpleName)

        cleanup:
        context.close()
    }

    void "test beans that replace the same interface both remain so primary decides"() {
        given:
        ApplicationContext context = buildContext(GREETER + """
@Singleton
@Primary
@Replaces(Greeter.class)
class MockGreeter implements Greeter {
}
""")
        def greeter = context.classLoader.loadClass("test.Greeter")

        expect:
        context.getBean(greeter).class.simpleName == "MockGreeter"
        context.getBeansOfType(greeter)*.class*.simpleName.toSet() == ["MockGreeter", "ReplacingGreeter"].toSet()

        cleanup:
        context.close()
    }

    void "test beans that replace the same interface without a preference are not unique"() {
        given:
        ApplicationContext context = buildContext(GREETER + """
@Singleton
@Replaces(Greeter.class)
class MockGreeter implements Greeter {
}
""")

        when:
        context.getBean(context.classLoader.loadClass("test.Greeter"))

        then:
        thrown(NonUniqueBeanException)

        cleanup:
        context.close()
    }

    private static final String HANDLER = """
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@interface Handler {
    String value();
}

interface AnyHandler {
}

@Singleton
@Handler("plain")
class PlainHandler implements AnyHandler {
}

@Singleton
@Handler("other")
class OtherHandler implements AnyHandler {
}

@Singleton
class PlainConsumer {
    final PlainHandler handler;
    final java.util.List<PlainHandler> handlers;

    PlainConsumer(@Handler("plain") PlainHandler handler, @Handler("plain") java.util.List<PlainHandler> handlers) {
        this.handler = handler;
        this.handlers = handlers;
    }
}
"""

    void "test a replacement satisfies the qualifier of the bean it replaces"() {
        given:
        ApplicationContext context = buildContext(HANDLER + """
@Singleton
@Replaces(PlainHandler.class)
class MockHandler extends PlainHandler {
}
""")
        def loader = context.classLoader
        def handlerType = loader.loadClass("test.PlainHandler")
        def mockType = loader.loadClass("test.MockHandler")
        def annotation = loader.loadClass("test.Handler")
        def plain = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, AnnotationValue.builder(annotation).value("plain").build())
        def other = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, AnnotationValue.builder(annotation).value("other").build())
        def consumer = context.getBean(loader.loadClass("test.PlainConsumer"))

        expect: "the qualifier of the replaced bean resolves the one replacement instance"
        consumer.handler.is(context.getBean(mockType))
        consumer.handlers*.class == [mockType]
        context.getBean(handlerType, plain).is(consumer.handler)
        context.containsBean(handlerType, plain)
        context.findBean(handlerType, plain).isPresent()
        context.getBeansOfType(handlerType, plain)*.class == [mockType]
        context.getBeanDefinitions(handlerType, plain)*.beanType == [mockType]

        and: "a wider type resolves the same way and other qualifiers are unaffected"
        def anyHandler = loader.loadClass("test.AnyHandler")
        context.getBean(anyHandler, plain).is(consumer.handler)
        context.getBean(anyHandler, other).class.simpleName == "OtherHandler"
        context.getBeansOfType(anyHandler, other).size() == 1
        context.getBeansOfType(anyHandler).size() == 2

        cleanup:
        context.close()
    }

    void "test a replacement keeps its own qualifier alongside the qualifier of the bean it replaces"() {
        given:
        ApplicationContext context = buildContext(HANDLER + """
@Singleton
@Handler("mock")
@Replaces(PlainHandler.class)
class MockHandler extends PlainHandler {
}
""")
        def loader = context.classLoader
        def handlerType = loader.loadClass("test.PlainHandler")
        def annotation = loader.loadClass("test.Handler")
        def plain = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, AnnotationValue.builder(annotation).value("plain").build())
        def mock = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, AnnotationValue.builder(annotation).value("mock").build())

        expect:
        context.getBean(handlerType, plain).is(context.getBean(handlerType, mock))

        cleanup:
        context.close()
    }

    void "test the end of a replacement chain satisfies the qualifier of the first bean"() {
        given:
        ApplicationContext context = buildContext(HANDLER + """
@Singleton
@Replaces(PlainHandler.class)
class MiddleHandler extends PlainHandler {
}

@Singleton
@Replaces(MiddleHandler.class)
class MockHandler extends MiddleHandler {
}
""")
        def loader = context.classLoader
        def consumer = context.getBean(loader.loadClass("test.PlainConsumer"))

        expect:
        consumer.handler.class.simpleName == "MockHandler"

        cleanup:
        context.close()
    }

    void "test the end of a longer replacement chain is the only bean left"() {
        given:
        ApplicationContext context = buildContext(HANDLER + """
@Singleton
@Replaces(PlainHandler.class)
class MiddleHandler extends PlainHandler {
}

@Singleton
@Replaces(MiddleHandler.class)
class MockHandler extends MiddleHandler {
}

@Singleton
@Replaces(MockHandler.class)
class FinalHandler extends MockHandler {
}
""")
        def loader = context.classLoader
        def handlerType = loader.loadClass("test.PlainHandler")
        def consumer = context.getBean(loader.loadClass("test.PlainConsumer"))

        expect:
        context.getBeansOfType(handlerType)*.class*.simpleName == ["FinalHandler"]
        consumer.handler.class.simpleName == "FinalHandler"

        cleanup:
        context.close()
    }

    void "test a bean of a wider type is still replaced by a bean outside of its cycle"() {
        given:
        ApplicationContext context = buildContext(GREETER + """
@Singleton
@Replaces(MockGreeter.class)
class FinalGreeter extends MockGreeter {
}

@Singleton
@Replaces(ReplacingGreeter.class)
class MockGreeter implements Greeter {
}
""")
        def greeter = context.classLoader.loadClass("test.Greeter")

        expect:
        context.getBeansOfType(greeter)*.class*.simpleName == ["FinalGreeter"]

        cleanup:
        context.close()
    }

    void "test beans that replace each other without a narrower target all remain"() {
        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

interface Thing {
}

@Singleton
@Replaces(B.class)
class A implements Thing {
}

@Singleton
@Replaces(C.class)
class B implements Thing {
}

@Singleton
@Replaces(A.class)
class C implements Thing {
}
""")

        expect:
        context.getBeansOfType(context.classLoader.loadClass("test.Thing")).size() == 3

        cleanup:
        context.close()
    }

    void "test a bean named by the replaces annotation resolves to its replacement"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesSpec'])

        expect:
        context.getBean(E, Qualifiers.byName("E1")) instanceof E1Replacement
        context.getBean(E, Qualifiers.byName("E2")) instanceof E2Replacement

        cleanup:
        context.close()
    }

    void "test a replacement does not take on the type arguments of the bean it replaces"() {
        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

interface Foo<T> {
}

@Singleton
class StringFoo implements Foo<String> {
}

@Singleton
@Replaces(StringFoo.class)
class IntegerFoo implements Foo<Integer> {
}
""")
        def foo = context.classLoader.loadClass("test.Foo")

        expect:
        context.getBeansOfType(foo, Qualifiers.byTypeArguments(String)).isEmpty()
        context.getBeansOfType(foo, Qualifiers.byTypeArguments(Integer)).size() == 1

        cleanup:
        context.close()
    }

    void "test a marker qualifier of the replaced bean selects the replacement"() {
        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@interface Marker {
}

@Singleton
@Marker
class MarkedBean {
}

@Singleton
@Replaces(MarkedBean.class)
class MockBean extends MarkedBean {
}

@Singleton
class MarkedConsumer {
    final MarkedBean bean;

    MarkedConsumer(@Marker MarkedBean bean) {
        this.bean = bean;
    }
}
""")
        def loader = context.classLoader
        def marker = loader.loadClass("test.Marker")
        def marked = loader.loadClass("test.MarkedBean")

        expect:
        context.getBean(loader.loadClass("test.MarkedConsumer")).bean.class.simpleName == "MockBean"
        context.getBean(marked, Qualifiers.byStereotype(marker)).class.simpleName == "MockBean"

        cleanup:
        context.close()
    }

    void "test a replacement does not take on the type arguments of a qualified bean it replaces"() {
        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

interface Foo<T> {
}

@Singleton
@Named("string")
class StringFoo implements Foo<String> {
}

@Singleton
@Replaces(StringFoo.class)
class IntegerFoo implements Foo<Integer> {
}
""")
        def foo = context.classLoader.loadClass("test.Foo")

        expect: "only the declared qualifier is inherited"
        context.getBeansOfType(foo, Qualifiers.byName("string"))*.class*.simpleName == ["IntegerFoo"]
        context.getBeansOfType(foo, Qualifiers.byTypeArguments(Integer))*.class*.simpleName == ["IntegerFoo"]
        context.getBeansOfType(foo, Qualifiers.byTypeArguments(String)).isEmpty()
        context.getBeansOfType(foo, Qualifiers.byQualifiers(Qualifiers.byName("string"), Qualifiers.byTypeArguments(String))).isEmpty()

        cleanup:
        context.close()
    }

    void "test a lookup selects a replacement the same way it selects the bean being replaced"() {
        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Kind {
    String value();
    int priority() default 1;
}

interface Handler {
}

@Singleton
@Named("plain")
class NamedHandler implements Handler {
}

@Singleton
@Replaces(NamedHandler.class)
class MockNamedHandler extends NamedHandler {
}

@Singleton
@Kind("plain")
class KindHandler implements Handler {
}

@Singleton
@Replaces(KindHandler.class)
class MockKindHandler extends KindHandler {
}

@Singleton
class KindConsumer {
    @Inject @Kind("plain") Handler implicitDefault;
    @Inject @Kind(value = "plain", priority = 1) Handler explicitDefault;
}
""")
        def loader = context.classLoader
        def handler = loader.loadClass("test.Handler")
        def consumer = context.getBean(loader.loadClass("test.KindConsumer"))
        def kind = loader.loadClass("test.Kind")

        expect: "a name ignores case"
        context.getBeansOfType(handler, Qualifiers.byName("plain"))*.class*.simpleName == ["MockNamedHandler"]
        context.getBeansOfType(handler, Qualifiers.byName("Plain"))*.class*.simpleName == ["MockNamedHandler"]
        context.getBeansOfType(handler, Qualifiers.byName("other")).isEmpty()

        and: "a member at its default matches whether or not it is written down"
        consumer.implicitDefault.class.simpleName == "MockKindHandler"
        consumer.explicitDefault.class.simpleName == "MockKindHandler"

        and: "a member with another value does not match"
        context.getBeansOfType(handler, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA,
                AnnotationValue.builder(kind).value("plain").member("priority", 2).build())).isEmpty()
        context.getBeansOfType(handler, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA,
                AnnotationValue.builder(kind).value("other").build())).isEmpty()

        cleanup:
        context.close()
    }

    void "test a non-binding member does not stop a replacement satisfying the qualifier of the bean it replaces"() {
        given:
        ApplicationContext context = buildContext("""
package test;

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Kind {
    String value();
    @NonBinding String note() default "";
}

interface Handler {
}

@Singleton
@Kind(value = "kept", note = "declared")
class KeptHandler implements Handler {
}

@Singleton
@Kind(value = "plain", note = "declared")
class KindHandler implements Handler {
}

@Singleton
@Replaces(KindHandler.class)
class MockKindHandler extends KindHandler {
}

@Singleton
class KindConsumer {
    @Inject @Kind(value = "plain", note = "written") Handler handler;
}
""")
        def loader = context.classLoader
        def handler = loader.loadClass("test.Handler")
        def kind = loader.loadClass("test.Kind")

        expect: "an injection point records the member as non-binding"
        context.getBean(loader.loadClass("test.KindConsumer")).handler.class.simpleName == "MockKindHandler"

        and: "a qualifier built without that record compares a bean by its binding members"
        context.getBeansOfType(handler, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA,
                AnnotationValue.builder(kind).value("kept").member("note", "written").build()))*.class*.simpleName == ["KeptHandler"]

        and: "so does it compare the bean that was replaced"
        context.getBeansOfType(handler, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA,
                AnnotationValue.builder(kind).value("plain").member("note", "written").build()))*.class*.simpleName == ["MockKindHandler"]

        and: "a binding member with another value does not match"
        context.getBeansOfType(handler, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA,
                AnnotationValue.builder(kind).value("other").member("note", "declared").build())).isEmpty()

        cleanup:
        context.close()
    }
}
