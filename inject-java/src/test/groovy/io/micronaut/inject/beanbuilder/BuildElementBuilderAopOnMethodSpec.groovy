package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.visitor.TypeElementVisitor

class BuildElementBuilderAopOnMethodSpec extends AbstractTypeElementSpec {

    void "test AOP applied to a type registered via the builder"() {
        given:
        def context = buildContext('''
package aopbuilder;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.type.MutableArgumentValue;
import jakarta.inject.Singleton;
import io.micronaut.inject.beanbuilder.ApplyAopToMe;
import java.lang.annotation.Retention;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
class Test {
    private final ApplyAopToMe applyAopToMe;
    
    Test(ApplyAopToMe applyAopToMe) {
        this.applyAopToMe = applyAopToMe;
    }
    
    public String hello(String name) {
        return applyAopToMe.hello(name);
    }
    
    public String plain(String name) {
        return applyAopToMe.plain(name);
    }
}

@InterceptorBean(Mutating.class)
class MutatingInterceptor implements MethodInterceptor<Object, Object> {
    @Override public Object intercept(MethodInvocationContext<Object,Object> context) {
        String m = context.stringValue(Mutating.class).orElse(null);
        MutableArgumentValue arg = context.getParameters().get(m);
        if(arg != null) {
            arg.setValue("changed");
        }
        return context.proceed();
    }
}

@Around
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Inherited
@interface Mutating {
    String value();
}

''')
        def test = getBean(context, "aopbuilder.Test")

        expect:
        test.hello("john") == "Hello changed"
        test.plain("john") == "Hello john"

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        [new ApplyAopToMethodVisitor()]
    }


}
