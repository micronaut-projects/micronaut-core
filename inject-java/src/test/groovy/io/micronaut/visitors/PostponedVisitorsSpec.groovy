package io.micronaut.visitors


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ProxyBeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.UnresolvedTypeKind
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.BeanDefinitionVisitor
import org.intellij.lang.annotations.Language
import spock.lang.PendingFeature

class PostponedVisitorsSpec extends AbstractTypeElementSpec {

    void 'test postpone generation of annotation metadata on introduction'() {
        when:
        def definition = buildBeanDefinition('test.SomeInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.visitors.*;
import test.SomeInterfaceConstants;

@IntroductionTest(SomeInterfaceConstants.PATH)
interface SomeInterface {

}

@ConstantGen
interface GenConstants {}
''')
        then:
        definition != null
        definition.stringValue(IntroductionTest).get() == 'generated'
    }

    void 'test postpone introspection generation implementing generated interface'() {
        when:
            def definition = buildBeanIntrospection('test.Walrus', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.visitors.Wither;
import io.micronaut.visitors.Builder;

@Introspected(builder = @Introspected.IntrospectionBuilder(
    builderClass = WalrusBuilder.class
))
@Wither
@Builder
public record Walrus (
    @NonNull
    String name,
    int age,
    byte[] chipInfo
) implements WalrusWither  {

    @Vetoed
    public static WalrusBuilder builder() {
        return new WalrusBuilder();
    }
}

''')
        then:
            definition
    }

    void 'test postpone introduction generation implementing generated interface'() {
        when:
        def context = buildContext('test.MyIntroduction' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.visitors.IntroductionTest;
import io.micronaut.visitors.IntroductionTestGen;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;


@IntroductionTestGen
class Foo {}

@IntroductionTest
interface MyIntroduction extends IntroductionTestParent  {
}


@InterceptorBean(IntroductionTest.class)
class IntroductionTestInterceptor
    implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "good";
    }
}
''')
        def introduction = getBean(context, 'test.MyIntroduction')
        def definition = getBeanDefinition(context, 'test.MyIntroduction')

        then:
        introduction.getParentMethod() == 'good'
        definition.getRequiredMethod("getParentMethod").hasAnnotation("SomeAnnotation")
    }

    void 'test postpone bean definition generation implementing generated interface'() {
        when:
        def context = buildContext('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.visitors.InterfaceGen;
import io.micronaut.visitors.IntroductionTestGen;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;


@InterfaceGen
class Foo {}

@Singleton
class MyBean implements GeneratedInterface  {
    @Override
    public Bar test(Bar bar) {
        return bar;
    }
}
''')
        def definition = getBeanDefinition(context, 'test.MyBean')

        then:
        definition.executableMethods.size() == 1
    }

    void "test information collecting visitor"() {
        when:
        def definition = buildBeanDefinition('example.Child', '''
package example;

import jakarta.inject.Singleton;
import io.micronaut.http.annotation.Controller;

@io.micronaut.visitors.GeneratorTrigger
class Trigger {}

// Parent is generated, we want to retrieve inherited annotations correctly
@Singleton
@Controller(Parent.BASE_PATH)
class Child implements Parent {

    @Override
    public TestModel hello() {
        return new TestModel("Hola!");
    }

}
''')
        then:
        definition.executableMethods.first().hasAnnotation("test.CustomAnn")
        CollectingVisitor.numVisited == 1
        CollectingVisitor.numMethodVisited == 1
        CollectingVisitor.getPath == "/get"
        CollectingVisitor.hasIntrospected
        CollectingVisitor.controllerPath == "/hello"
    }

    void "test information collecting visitor not through parent"() {
        when:
        def definition = buildBeanDefinition('example.Child', '''
package example;

import jakarta.inject.Singleton;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@io.micronaut.visitors.GeneratorTrigger
class Trigger {}

// Parent is generated, we want to retrieve the value correctly
@Singleton
@Controller(Parent.BASE_PATH)
class Child {

    @Get("/get")
    public String hello() {
        return "Hola!";
    }

}
''')
        then:
        definition.executableMethods.first().hasAnnotation("test.CustomAnn")
        CollectingVisitor.numVisited == 1
        CollectingVisitor.numMethodVisited == 1
        CollectingVisitor.getPath == "/get"
        CollectingVisitor.controllerPath == "/hello"
    }

}
