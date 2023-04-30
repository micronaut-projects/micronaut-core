package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBinding
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Qualifier
import jakarta.inject.Scope

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class AddStereotypesFromVisitorSpec extends AbstractTypeElementSpec {

    void "test that adding annotations to an annotate results in them being stereotypes"() {
        given:
        def context = buildContext('''
package addstereotype;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.inject.annotation.MyQualifier;
import io.micronaut.inject.annotation.MyScope;
import io.micronaut.inject.annotation.MyAdvice;
import io.micronaut.inject.annotation.MyAnnotation;
import io.micronaut.aop.InterceptorBean;
import java.util.Locale;

@MyScope
class TestBean {
    @MyQualifier public Other other;
}

@MyQualifier
class Other {}

@MyAnnotation
class StereotypeTest {}

@MyScope
class AdvisedBean {
    @MyAdvice
    public String test(String name) {
        return name;
    }
}

@InterceptorBean(MyAdvice.class)
class MyInterceptor implements MethodInterceptor<Object, Object> {
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
        Object[] parameterValues = context.getParameterValues();
        parameterValues[0] = parameterValues[0].toString().toUpperCase(Locale.ENGLISH);
        return context.proceed();
    }
}
''')
        expect:
        getBean(context, 'addstereotype.StereotypeTest') != null
        getBean(context, 'addstereotype.TestBean').other != null
        getBeanDefinition(context, 'addstereotype.StereotypeTest')
            .getAnnotationNameByStereotype(AnnotationUtil.SCOPE)
            .get() == MyScope.name
        getBeanDefinition(context, 'addstereotype.TestBean')
                .injectedFields.first().annotationMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
        getBean(context, 'addstereotype.AdvisedBean') instanceof Intercepted
        getBean(context, 'addstereotype.AdvisedBean').test("foo") == "FOO"

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new QualifierAddingVisitor(), new ScopeAddingVisitor(), new InterceptorAddingVisitor()]
    }


    static class QualifierAddingVisitor implements TypeElementVisitor<Object, MyQualifier> {
        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement(MyQualifier).ifPresent({ ClassElement ce ->
                ce.annotate(Qualifier)
            })
        }
    }

    static class ScopeAddingVisitor implements TypeElementVisitor<MyScope, Object> {
        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement(MyScope).ifPresent({ ClassElement ce ->
                ce.annotate(Scope)
            })
            visitorContext.getClassElement(MyAnnotation).ifPresent({ ClassElement ce ->
                ce.annotate(MyScope)
            })
        }
    }

    static class InterceptorAddingVisitor implements TypeElementVisitor<Object, MyAdvice> {
        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement(MyAdvice).ifPresent({ ClassElement ce ->
                ce.annotate(InterceptorBinding) { AnnotationValueBuilder builder ->
                    builder.value(MyAdvice)
                }
            })
        }
    }
}
@Retention(RetentionPolicy.RUNTIME)
@interface MyQualifier {}

@Retention(RetentionPolicy.RUNTIME)
@interface MyScope {}

@Retention(RetentionPolicy.RUNTIME)
@interface MyAdvice {}

@Retention(RetentionPolicy.RUNTIME)
@interface MyAnnotation {}
