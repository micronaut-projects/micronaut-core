package io.micronaut.visitors


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.PendingFeature

class PostponedVisitorsSpec extends AbstractTypeElementSpec {

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

        then:
        introduction.getParentMethod() == 'good'
    }
}
