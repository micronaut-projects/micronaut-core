package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition

import java.lang.annotation.Annotation

/**
 * The declared metadata of an {@link EvaluatedAnnotationMetadata} narrows to the element and keeps evaluating the
 * expressions of the annotation values.
 */
class EvaluatedAnnotationMetadataDeclaredSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.inject.annotation.ExpressionCarrier;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Label(value = "#{ 'class' + '-label' }", scope = "#{ 'class' + '-scope' }")
@Tag("#{ 'class' + '-tag' }")
@ExpressionCarrier("#{ 2 + 2 }")
class Test {

    @Executable
    @Label(value = "#{ 'method' + '-label' }", names = {"#{ 'o' + 'ne' }", "two"})
    @Tag("#{ 'method-tag' + '-one' }")
    @Tag("method-tag-two")
    @Marked(level = "#{ 'hi' + 'gh' }")
    @ExpressionCarrier("#{ 1 + 1 }")
    void declared() {
    }

    @Executable
    void inherited() {
    }

    @Executable
    @Label(value = "#{ 'mixed' + '-label' }", scope = "${test.scope}")
    void mixed() {
    }

    @Executable
    @Label("#{ #name + '-label' }")
    void arguments(String name) {
    }
}

@Singleton
@Executable
@Label(value = "#{ 'class' + '-label' }", names = {"#{ 'o' + 'ne' }", "two"})
class ClassOnly {

    void inherited() {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Label {
    String value();

    String scope() default "default-scope";

    String[] names() default {};
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Tags.class)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Tags {
    Tag[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Label("marked-label")
@interface Marked {
    String level() default "low";
}
'''

    private static final Map<String, Object> PROPERTIES = ['test.scope': 'resolved-scope']

    void "test the declared metadata of an expression aware method narrows to the method and evaluates expressions"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        Class<? extends Annotation> label = context.classLoader.loadClass('test.Label')
        AnnotationMetadata metadata = method(context, 'test.Test', 'declared').annotationMetadata
        AnnotationMetadata declared = metadata.declaredMetadata

        expect: "the method metadata is expression aware and combines the method and the class"
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == 'method-label'
        metadata.findAnnotation(label).get().stringValue('scope').get() == 'class-scope'
        metadata.stringValue(label).get() == 'method-label'
        metadata.getAnnotationValuesByName('test.Tag').collect { it.stringValue().get() } as Set ==
                ['method-tag-one', 'method-tag-two', 'class-tag'] as Set

        and: "the declared metadata excludes the class annotations and the members only the class declares"
        !declared.is(metadata)
        declared.getAnnotationValuesByName('test.Tag').collect { it.stringValue().get() } == ['method-tag-one', 'method-tag-two']
        declared.findAnnotation(label).get().stringValue('scope').isEmpty()
        declared.stringValue(label, 'scope').isEmpty()
        declared.synthesize(label).scope() == 'default-scope'
        !declared.hasAnnotation('jakarta.inject.Singleton')

        and: "the declared metadata evaluates expressions"
        declared.findAnnotation(label).get().stringValue().get() == 'method-label'
        declared.findAnnotation('test.Label').get().stringValue().get() == 'method-label'
        declared.getAnnotation(label).stringValue().get() == 'method-label'
        declared.findDeclaredAnnotation(label).get().stringValue().get() == 'method-label'
        declared.getDeclaredAnnotation(label).stringValue().get() == 'method-label'
        declared.getDeclaredAnnotation('test.Label').stringValue().get() == 'method-label'
        declared.stringValue(label).get() == 'method-label'
        declared.stringValue('test.Label').get() == 'method-label'
        declared.getValue(label, String).get() == 'method-label'
        declared.stringValues(label, 'names') == ['one', 'two'] as String[]
        declared.stringValues('test.Label', 'names') == ['one', 'two'] as String[]
        declared.findAnnotation(label).get().stringValues('names') == ['one', 'two'] as String[]
        declared.getAnnotationValuesByType(label)*.stringValue()*.get() == ['method-label']
        declared.getAnnotationValuesByStereotype('test.Label')*.annotationName == ['test.Marked']
        declared.getAnnotationValuesByStereotype('test.Label').first().stringValue('level').get() == 'high'

        and: "the annotations synthesized from the declared metadata evaluate expressions"
        declared.synthesize(label).value() == 'method-label'
        declared.synthesize(label).names() == ['one', 'two'] as String[]
        declared.synthesizeDeclared(label).value() == 'method-label'
        declared.synthesize(label, 'test.Label').value() == 'method-label'
        declared.synthesizeDeclared(label, 'test.Label').value() == 'method-label'
        declared.synthesizeAll().findAll { it instanceof ExpressionCarrier }*.value() == ['2']
        declared.synthesizeDeclared().findAll { it instanceof ExpressionCarrier }*.value() == ['2']

        and: "the declared annotations of the method metadata evaluate expressions"
        metadata.synthesizeDeclared(label).value() == 'method-label'
        metadata.synthesizeDeclared(label).names() == ['one', 'two'] as String[]
        metadata.synthesizeDeclared(label, 'test.Label').value() == 'method-label'
        metadata.findDeclaredAnnotation(label).get().stringValue().get() == 'method-label'
        metadata.getDeclaredAnnotation(label).stringValue().get() == 'method-label'
        metadata.getDeclaredAnnotation('test.Label').stringValue().get() == 'method-label'

        and: "all the annotations synthesized from the method metadata evaluate expressions, one per element as the unwrapped metadata answers"
        metadata.synthesizeAll().findAll { it instanceof ExpressionCarrier }*.value() == ['2', '4']
        metadata.synthesizeDeclared().findAll { it instanceof ExpressionCarrier }*.value() == ['2', '4']
        metadata.synthesizeAll()*.annotationType() == metadata.getTargetAnnotationMetadata().synthesizeAll()*.annotationType()
        metadata.synthesizeDeclared()*.annotationType() == metadata.getTargetAnnotationMetadata().synthesizeDeclared()*.annotationType()
        metadata.synthesizeAll().find { it instanceof ExpressionCarrier } == metadata.synthesize(ExpressionCarrier)
        metadata.synthesizeAll().find { it instanceof ExpressionCarrier }.hashCode() == metadata.synthesize(ExpressionCarrier).hashCode()

        and: "narrowing the declared metadata again keeps evaluating expressions"
        declared.declaredMetadata.stringValue(label).get() == 'method-label'
        declared.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'method-label'

        and: "the declared metadata is cached"
        metadata.declaredMetadata.is(declared)

        and: "the full metadata still evaluates expressions"
        metadata.findAnnotation(label).get().stringValue().get() == 'method-label'
        metadata.synthesize(label).value() == 'method-label'

        cleanup:
        context.close()
    }

    void "test the declared metadata of an expression aware method without annotations excludes the class annotations"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        Class<? extends Annotation> label = context.classLoader.loadClass('test.Label')
        AnnotationMetadata metadata = method(context, 'test.Test', 'inherited').annotationMetadata

        expect:
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == 'class-label'
        metadata.stringValue(label).get() == 'class-label'
        !metadata.declaredMetadata.hasAnnotation(label)
        metadata.declaredMetadata.findAnnotation(label).isEmpty()
        metadata.declaredMetadata.stringValue(label).isEmpty()
        metadata.declaredMetadata.synthesize(label) == null

        cleanup:
        context.close()
    }

    void "test the declared metadata of a method that reuses the expression aware metadata of its class evaluates expressions"() {
        given:
        ApplicationContext context = buildContext('test.ClassOnly', SOURCE, false, PROPERTIES)
        Class<? extends Annotation> label = context.classLoader.loadClass('test.Label')
        AnnotationMetadata metadata = method(context, 'test.ClassOnly', 'inherited').annotationMetadata
        AnnotationMetadata declared = metadata.declaredMetadata

        expect: "the method declares no annotation, its metadata is the one of the class"
        metadata instanceof EvaluatedAnnotationMetadata
        !(metadata.getTargetAnnotationMetadata() instanceof AnnotationMetadataHierarchy)
        metadata.findAnnotation(label).get().stringValue().get() == 'class-label'

        and: "the declared metadata is the one of the class and evaluates expressions"
        declared.findAnnotation(label).get().stringValue().get() == 'class-label'
        declared.stringValue(label).get() == 'class-label'
        declared.stringValues(label, 'names') == ['one', 'two'] as String[]
        declared.synthesize(label).value() == 'class-label'
        declared.synthesizeDeclared(label).value() == 'class-label'
        metadata.synthesizeDeclared(label).value() == 'class-label'
        metadata.synthesizeDeclared(label).names() == ['one', 'two'] as String[]

        cleanup:
        context.close()
    }

    void "test the declared metadata evaluates an expression and resolves a placeholder of the same method"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        Class<? extends Annotation> label = context.classLoader.loadClass('test.Label')
        AnnotationMetadata metadata = method(context, 'test.Test', 'mixed').annotationMetadata
        AnnotationMetadata declared = metadata.declaredMetadata

        expect: "the expression aware metadata wraps the environment aware metadata"
        metadata instanceof EvaluatedAnnotationMetadata
        ((EvaluatedAnnotationMetadata) metadata).annotationMetadata instanceof AbstractEnvironmentAnnotationMetadata
        metadata.findAnnotation(label).get().stringValue().get() == 'mixed-label'
        metadata.findAnnotation(label).get().stringValue('scope').get() == 'resolved-scope'

        and: "the declared metadata evaluates the expression and resolves the placeholder"
        declared.findAnnotation(label).get().stringValue().get() == 'mixed-label'
        declared.findAnnotation(label).get().stringValue('scope').get() == 'resolved-scope'
        declared.stringValue(label).get() == 'mixed-label'
        declared.stringValue(label, 'scope').get() == 'resolved-scope'
        declared.synthesize(label).value() == 'mixed-label'
        declared.synthesize(label).scope() == 'resolved-scope'
        metadata.synthesizeDeclared(label).value() == 'mixed-label'
        metadata.synthesizeDeclared(label).scope() == 'resolved-scope'
        !declared.hasAnnotation('jakarta.inject.Singleton')

        when: "the environment aware metadata wraps the expression aware metadata, which the context does not do"
        AnnotationMetadata reversed = new AbstractEnvironmentAnnotationMetadata(metadata) {
            @Override
            protected io.micronaut.context.env.Environment getEnvironment() {
                return context.environment
            }
        }

        then: "the declared metadata answers the value reads like the full metadata does"
        reversed.stringValue(label).get() == 'mixed-label'
        reversed.stringValue(label, 'scope').get() == 'resolved-scope'
        reversed.declaredMetadata.stringValue(label).get() == 'mixed-label'
        reversed.declaredMetadata.stringValue(label, 'scope').get() == 'resolved-scope'
        !reversed.declaredMetadata.hasAnnotation('jakarta.inject.Singleton')

        cleanup:
        context.close()
    }

    void "test the declared metadata keeps every layer of a hierarchy that delegates declared to all elements"() {
        given:
        MutableAnnotationMetadata first = new MutableAnnotationMetadata()
        first.addDeclaredAnnotation('test.First', [value: 'first'])
        first.hasEvaluatedExpressions = true
        MutableAnnotationMetadata second = new MutableAnnotationMetadata()
        second.addDeclaredAnnotation('test.Second', [value: 'second'])
        AnnotationMetadata metadata = EvaluatedAnnotationMetadata.wrapIfNecessary(new AnnotationMetadataHierarchy(true, first, second))

        expect:
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.declaredAnnotationNames == ['test.First', 'test.Second'] as Set
        metadata.declaredMetadata.is(metadata)
        metadata.declaredMetadata.stringValue('test.First').get() == 'first'
        metadata.declaredMetadata.stringValue('test.Second').get() == 'second'
    }

    void "test the declared metadata of a copy with arguments evaluates against the arguments of the copy"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        Class<? extends Annotation> label = context.classLoader.loadClass('test.Label')
        EvaluatedAnnotationMetadata metadata = (EvaluatedAnnotationMetadata) method(context, 'test.Test', 'arguments').annotationMetadata
        Object bean = context.getBean(context.classLoader.loadClass('test.Test'))

        when:
        AnnotationMetadata first = metadata.withArguments(bean, ['first'] as Object[])
        AnnotationMetadata second = metadata.withArguments(bean, ['second'] as Object[])

        then:
        first.findAnnotation(label).get().stringValue().get() == 'first-label'
        first.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'first-label'
        first.declaredMetadata.stringValue(label).get() == 'first-label'
        first.synthesizeDeclared(label).value() == 'first-label'
        second.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'second-label'
        first.declaredMetadata.findAnnotation(label).get().stringValue().get() == 'first-label'

        cleanup:
        context.close()
    }

    private static io.micronaut.inject.ExecutableMethod<?, ?> method(ApplicationContext context, String type, String name) {
        Class<?> beanType = context.classLoader.loadClass(type)
        context.getBean(beanType)
        BeanDefinition<?> definition = context.getBeanDefinition(beanType)
        return definition.getRequiredMethod(name, definition.executableMethods.find { it.methodName == name }.argumentTypes)
    }
}
