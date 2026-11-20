/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.ProxyBeanDefinition
import spock.lang.Unroll

import java.lang.annotation.Annotation

/**
 * The annotation metadata an interceptor reads from its invocation context evaluates the expressions of the
 * annotation values, also when the annotations of the intercepted method or of its class carry property
 * placeholders and the metadata is therefore environment aware as well.
 */
class EvaluatedAnnotationMetadataInterceptedSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
class Plain {

    @Traced
    @Label("#{ 'eval' + 'uated' }")
    public void run() {
    }
}

@Singleton
class Test {

    @Traced
    @Label("#{ 'eval' + 'uated' }")
    @Note("${test.note}")
    public void otherAnnotation() {
    }

    @Traced
    @Label(value = "#{ 'eval' + 'uated' }", scope = "${test.scope}")
    public void sameAnnotation() {
    }

    @Traced
    @Label(value = "#{ #name + '-label' }", scope = "${test.scope}")
    public void arguments(String name) {
    }

    @Traced
    @Label("${test.prefix:pre}-#{ 'x' }")
    public void sameMember() {
    }

    @Traced
    @Note("${test.note}")
    public void argumentAnnotation(@Label("#{ env['test.note'] + '-argument' }") String value) {
    }
}

@Singleton
class NoExpressions {

    @Traced
    public void run() {
    }
}

@Prototype
class PrototypeScoped {

    @Traced
    @Label("#{ 'eval' + 'uated' }")
    @Note("${test.note}")
    public void run() {
    }
}

@Singleton
@Note("${test.note}")
class ClassLevel {

    @Traced
    @Label("#{ 'eval' + 'uated' }")
    public void run() {
    }
}

@Singleton
@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {

    public final List<AnnotationMetadata> seen = new ArrayList<>();

    public final List<AnnotationMetadata> seenArguments = new ArrayList<>();

    public final List<ExecutableMethod<?, ?>> seenMethods = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        seen.add(context.getAnnotationMetadata());
        seenMethods.add(context.getExecutableMethod());
        if (context.getArguments().length > 0) {
            seenArguments.add(context.getArguments()[0].getAnnotationMetadata());
        }
        return context.proceed();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
AROUND
@interface Traced {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@interface Label {
    String value();

    String scope() default "default-scope";
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Note {
    String value();
}
'''

    private static final Map<String, Object> PROPERTIES = ['test.scope': 'resolved-scope', 'test.note': 'resolved-note']

    private static final List<String> ADVICE = ['@Around', '@Around(proxyTarget = true)']

    @Unroll
    void "test an interceptor reads an evaluated expression without any placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')

        when:
        getBean(context, 'test.Plain').run()
        AnnotationMetadata metadata = seen(context).last()

        then:
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == 'evaluated'
        metadata.stringValue(label).get() == 'evaluated'
        metadata.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'evaluated'
        metadata.declaredMetadata.stringValue(label).get() == 'evaluated'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads an evaluated expression when another annotation of the method has a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')
        Class<? extends Annotation> note = annotationType(context, 'Note')

        when:
        getBean(context, 'test.Test').otherAnnotation()
        AnnotationMetadata metadata = seen(context).last()
        AnnotationMetadata declared = metadata.declaredMetadata

        then: "the expression aware metadata wraps the environment aware metadata"
        metadata instanceof EvaluatedAnnotationMetadata
        ((EvaluatedAnnotationMetadata) metadata).annotationMetadata instanceof AbstractEnvironmentAnnotationMetadata

        and: "the expression is evaluated"
        metadata.findAnnotation(label).get().stringValue().get() == 'evaluated'
        metadata.stringValue(label).get() == 'evaluated'
        metadata.synthesize(label).value() == 'evaluated'

        and: "the placeholder is resolved"
        metadata.findAnnotation(note).get().stringValue().get() == 'resolved-note'
        metadata.stringValue(note).get() == 'resolved-note'
        metadata.synthesize(note).value() == 'resolved-note'

        and: "the declared metadata agrees"
        declared.findAnnotation(label).get().stringValue().get() == 'evaluated'
        declared.stringValue(label).get() == 'evaluated'
        declared.synthesize(label).value() == 'evaluated'
        declared.findAnnotation(note).get().stringValue().get() == 'resolved-note'
        declared.stringValue(note).get() == 'resolved-note'
        declared.synthesize(note).value() == 'resolved-note'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads an evaluated expression when another member of the annotation has a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')

        when:
        getBean(context, 'test.Test').sameAnnotation()
        AnnotationMetadata metadata = seen(context).last()
        AnnotationMetadata declared = metadata.declaredMetadata

        then: "the expression is evaluated and the placeholder is resolved"
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == 'evaluated'
        metadata.findAnnotation(label).get().stringValue('scope').get() == 'resolved-scope'
        metadata.stringValue(label).get() == 'evaluated'
        metadata.stringValue(label, 'scope').get() == 'resolved-scope'
        metadata.synthesize(label).value() == 'evaluated'
        metadata.synthesize(label).scope() == 'resolved-scope'

        and: "the declared metadata agrees"
        declared.findAnnotation(label).get().stringValue().get() == 'evaluated'
        declared.findAnnotation(label).get().stringValue('scope').get() == 'resolved-scope'
        declared.stringValue(label).get() == 'evaluated'
        declared.stringValue(label, 'scope').get() == 'resolved-scope'
        declared.synthesize(label).value() == 'evaluated'
        declared.synthesize(label).scope() == 'resolved-scope'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads an evaluated expression when an annotation of the class has a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')
        Class<? extends Annotation> note = annotationType(context, 'Note')

        when:
        getBean(context, 'test.ClassLevel').run()
        AnnotationMetadata metadata = seen(context).last()
        AnnotationMetadata declared = metadata.declaredMetadata

        then: "the expression is evaluated and the placeholder of the class is resolved"
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == 'evaluated'
        metadata.stringValue(label).get() == 'evaluated'
        metadata.synthesize(label).value() == 'evaluated'
        metadata.findAnnotation(note).get().stringValue().get() == 'resolved-note'
        metadata.stringValue(note).get() == 'resolved-note'

        and: "the declared metadata agrees and excludes the annotation of the class"
        declared.findAnnotation(label).get().stringValue().get() == 'evaluated'
        declared.stringValue(label).get() == 'evaluated'
        declared.synthesize(label).value() == 'evaluated'
        !declared.hasAnnotation(note)

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads an expression evaluated against the arguments of its own invocation for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')
        def bean = getBean(context, 'test.Test')

        when:
        bean.arguments('first')
        bean.arguments('second')
        AnnotationMetadata first = seen(context)[0]
        AnnotationMetadata second = seen(context)[1]

        then: "the metadata of the first invocation, read after the second one, still evaluates against its own arguments"
        first.findAnnotation(label).get().stringValue().get() == 'first-label'
        first.stringValue(label).get() == 'first-label'
        first.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'first-label'
        first.declaredMetadata.stringValue(label).get() == 'first-label'

        and: "the metadata of the second invocation evaluates against its arguments"
        second.findAnnotation(label).get().stringValue().get() == 'second-label'
        second.stringValue(label).get() == 'second-label'
        second.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'second-label'
        second.declaredMetadata.stringValue(label).get() == 'second-label'

        and: "the placeholder is resolved"
        first.findAnnotation(label).get().stringValue('scope').get() == 'resolved-scope'
        second.declaredMetadata.stringValue(label, 'scope').get() == 'resolved-scope'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads the expression of a member that mixes it with a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')

        when:
        getBean(context, 'test.Test').sameMember()
        AnnotationMetadata metadata = seen(context).last()

        // Characterises a known limitation the pull request leaves as it is: if a member mixing both is ever
        // resolved fully, this feature fails by design and should expect the resolved placeholder
        then: "the member is an expression as a whole: the expression is evaluated, the placeholder is kept as written"
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == '${test.prefix:pre}-x'
        metadata.stringValue(label).get() == '${test.prefix:pre}-x'
        metadata.declaredMetadata.findAnnotation(label).get().stringValue().get() == '${test.prefix:pre}-x'
        metadata.declaredMetadata.stringValue(label).get() == '${test.prefix:pre}-x'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads an evaluated expression for each instance of a prototype bean with a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')
        Class<? extends Annotation> note = annotationType(context, 'Note')

        when: "each instance configures the shared executable method again"
        def firstInstance = getBean(context, 'test.PrototypeScoped')
        def secondInstance = getBean(context, 'test.PrototypeScoped')
        firstInstance.run()
        secondInstance.run()

        then:
        !firstInstance.is(secondInstance)
        seen(context).size() == 2
        seen(context).every { AnnotationMetadata metadata ->
            metadata instanceof EvaluatedAnnotationMetadata &&
                ((EvaluatedAnnotationMetadata) metadata).annotationMetadata instanceof AbstractEnvironmentAnnotationMetadata &&
                metadata.stringValue(label).get() == 'evaluated' &&
                metadata.declaredMetadata.stringValue(label).get() == 'evaluated' &&
                metadata.stringValue(note).get() == 'resolved-note'
        }

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test the executable method of the bean definition evaluates an expression and resolves a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')
        Class<? extends Annotation> note = annotationType(context, 'Note')

        when: "the bean definition configures the environment before the bean context"
        AnnotationMetadata metadata = context.getBeanDefinition(context.classLoader.loadClass('test.Test'))
            .findMethod('otherAnnotation').get().annotationMetadata

        then:
        metadata instanceof EvaluatedAnnotationMetadata
        ((EvaluatedAnnotationMetadata) metadata).annotationMetadata instanceof AbstractEnvironmentAnnotationMetadata
        metadata.stringValue(label).get() == 'evaluated'
        metadata.stringValue(note).get() == 'resolved-note'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test an interceptor reads an evaluated expression in a parameter annotation of a method with a placeholder for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)
        Class<? extends Annotation> label = annotationType(context, 'Label')
        Class<? extends Annotation> note = annotationType(context, 'Note')

        when:
        getBean(context, 'test.Test').argumentAnnotation('value')
        AnnotationMetadata argument = seenArguments(context).last()

        then: "the expression of the parameter annotation is evaluated with the bean context"
        argument instanceof EvaluatedAnnotationMetadata
        argument.stringValue(label).get() == 'resolved-note-argument'

        and: "the placeholder of the method is resolved"
        seen(context).last().stringValue(note).get() == 'resolved-note'

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test each context evaluates the metadata of the same compiled intercepted class with its own properties for #around"() {
        given: "two contexts over the same compiled classes, whose generated executable methods are held in static fields"
        ApplicationContext first = buildInterceptedContext(around)
        ApplicationContext second = buildContextWithSameClasses(first, PROPERTIES + ['test.note': 'other-note'])
        Class<? extends Annotation> label = annotationType(first, 'Label')
        Class<? extends Annotation> note = annotationType(first, 'Note')

        when: "both build their proxy before either is invoked"
        def firstBean = getBean(first, 'test.Test')
        def secondBean = getBean(second, 'test.Test')
        firstBean.otherAnnotation()
        secondBean.otherAnnotation()
        AnnotationMetadata firstMetadata = seen(first).last()
        AnnotationMetadata secondMetadata = seen(second).last()

        then:
        firstMetadata instanceof EvaluatedAnnotationMetadata
        firstMetadata.stringValue(label).get() == 'evaluated'
        firstMetadata.stringValue(note).get() == 'resolved-note'
        secondMetadata instanceof EvaluatedAnnotationMetadata
        secondMetadata.stringValue(label).get() == 'evaluated'
        secondMetadata.stringValue(note).get() == 'other-note'

        cleanup:
        second?.close()
        first?.close()

        where:
        around << ADVICE
    }

    @Unroll
    void "test the shared executable method of a definition without placeholders or expressions keeps no bean context for #around"() {
        given:
        ApplicationContext context = buildInterceptedContext(around)

        when:
        getBean(context, 'test.NoExpressions').run()
        def method = seenMethods(context).last()

        then: "the executable methods of such a definition are held in a static field shared by every context"
        method.getClass().name == 'io.micronaut.context.AbstractExecutableMethodsDefinition$DispatchedExecutableMethod'
        method.@beanContext == null

        cleanup:
        context.close()

        where:
        around << ADVICE
    }

    private ApplicationContext buildContextWithSameClasses(ApplicationContext context, Map<String, Object> properties) {
        ClassLoader classLoader = context.classLoader
        Set<Class<?>> referenceTypes = context.beanDefinitionReferences
            .collect { it.getClass() }
            .findAll { it.classLoader.is(classLoader) } as Set<Class<?>>
        // the context lists no reference for the target of a proxy target proxy: take it from the proxy
        context.getBeanDefinitions(Object)
            .findAll { it instanceof ProxyBeanDefinition }
            .each { referenceTypes << ((ProxyBeanDefinition) it).targetDefinitionType }
        List<BeanDefinitionReference> references = referenceTypes
            .collect { (BeanDefinitionReference) it.getDeclaredConstructor().newInstance() }
        return ApplicationContext.builder()
            .classLoader(classLoader)
            .environments('test')
            .properties(properties)
            .beanDefinitionsProvider { references + builtInBeanReferences }
            .start()
    }

    private ApplicationContext buildInterceptedContext(String around) {
        return buildContext('test.Test', SOURCE.replace('AROUND', around), false, PROPERTIES)
    }

    private static Class<? extends Annotation> annotationType(ApplicationContext context, String simpleName) {
        return (Class<? extends Annotation>) context.classLoader.loadClass("test.$simpleName")
    }

    private static List<AnnotationMetadata> seen(ApplicationContext context) {
        return context.getBean(context.classLoader.loadClass('test.TracedInterceptor')).seen
    }

    private static List<ExecutableMethod<?, ?>> seenMethods(ApplicationContext context) {
        return context.getBean(context.classLoader.loadClass('test.TracedInterceptor')).seenMethods
    }

    private static List<AnnotationMetadata> seenArguments(ApplicationContext context) {
        return context.getBean(context.classLoader.loadClass('test.TracedInterceptor')).seenArguments
    }
}
