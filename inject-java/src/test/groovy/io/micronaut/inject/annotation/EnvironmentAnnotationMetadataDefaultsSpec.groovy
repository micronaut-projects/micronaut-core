package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

class EnvironmentAnnotationMetadataDefaultsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Role("${test.class-role}")
@Tag("class-tag")
class Test {

    @Executable
    @Role("${test.method-role}")
    @Tag("method-tag-one")
    @Tag("method-tag-two")
    @Secure
    void declared() {
    }

    @Executable
    void inherited() {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Role {
    String value();

    String scope() default "${test.scope}";
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
@Role("${test.secure-role}")
@interface Secure {
}
'''

    private static final Map<String, Object> PROPERTIES = [
            'test.class-role' : 'class-role',
            'test.method-role': 'method-role',
            'test.secure-role': 'secure-role',
            'test.scope'      : 'resolved-scope',
    ]

    void "test the declared metadata of an environment aware method narrows to the method and resolves placeholders"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        BeanDefinition<?> definition = context.getBeanDefinition(context.classLoader.loadClass('test.Test'))
        AnnotationMetadata metadata = definition.getRequiredMethod('declared').annotationMetadata
        AnnotationMetadata declared = metadata.declaredMetadata

        expect: "the method metadata is environment aware and combines the method and the class"
        metadata instanceof AbstractEnvironmentAnnotationMetadata
        metadata.stringValue('test.Role').get() == 'method-role'
        metadata.getAnnotationValuesByName('test.Tag').collect { it.stringValue().get() } as Set ==
                ['method-tag-one', 'method-tag-two', 'class-tag'] as Set

        and: "the declared metadata excludes the class annotations"
        !declared.is(metadata)
        declared.getAnnotationValuesByName('test.Tag').collect { it.stringValue().get() } == ['method-tag-one', 'method-tag-two']

        and: "the declared metadata still resolves placeholders"
        declared.stringValue('test.Role').get() == 'method-role'
        declared.findAnnotation('test.Role').get().stringValue().get() == 'method-role'
        declared.getAnnotationValuesByStereotype('test.Role')*.annotationName == ['test.Secure']

        and: "narrowing the declared metadata again keeps resolving placeholders"
        declared.declaredMetadata.stringValue('test.Role').get() == 'method-role'
        declared.declaredMetadata.findDeclaredAnnotation('test.Role').get().stringValue().get() == 'method-role'

        cleanup:
        context.close()
    }

    void "test the declared metadata of an environment aware method without annotations excludes the class annotations"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        BeanDefinition<?> definition = context.getBeanDefinition(context.classLoader.loadClass('test.Test'))
        AnnotationMetadata metadata = definition.getRequiredMethod('inherited').annotationMetadata

        expect:
        metadata instanceof AbstractEnvironmentAnnotationMetadata
        metadata.stringValue('test.Role').get() == 'class-role'
        !metadata.declaredMetadata.hasAnnotation('test.Role')
        metadata.declaredMetadata.stringValue('test.Role').isEmpty()

        cleanup:
        context.close()
    }

    void "test the default methods of environment aware metadata delegate to the backing metadata"() {
        given:
        ApplicationContext context = buildContext('test.Test', SOURCE, false, PROPERTIES)
        BeanDefinition<?> definition = context.getBeanDefinition(context.classLoader.loadClass('test.Test'))
        ExecutableMethod<?, ?> method = definition.getRequiredMethod('declared')
        AnnotationMetadata metadata = method.annotationMetadata
        AnnotationMetadata backing = ((AbstractEnvironmentAnnotationMetadata) metadata).annotationMetadata

        expect:
        !metadata.isEmpty()
        metadata.hasPropertyExpressions()
        metadata.stereotypeAnnotationNames == backing.stereotypeAnnotationNames
        metadata.stereotypeAnnotationNames.contains('test.Role')
        metadata.declaredStereotypeAnnotationNames == backing.declaredStereotypeAnnotationNames
        metadata.declaredStereotypeAnnotationNames.contains('test.Role')
        metadata.getAnnotationValuesByStereotype('test.Role')*.annotationName ==
                backing.getAnnotationValuesByStereotype('test.Role')*.annotationName
        metadata.getAnnotationValuesByStereotype('test.Role').find { it.annotationName == 'test.Role' }.stringValue().get() == 'class-role'
        metadata.getDefaultValues('test.Role') == backing.getDefaultValues('test.Role')
        !metadata.getDefaultValues('test.Role').isEmpty()
        metadata.isRepeatableAnnotation('test.Tag')
        metadata.findRepeatableAnnotation('test.Tag').get() == 'test.Tags'
        !metadata.isRepeatableAnnotation('test.Role')

        and: "synthesized annotations resolve placeholders"
        Class<? extends java.lang.annotation.Annotation> role = context.classLoader.loadClass('test.Role')
        metadata.synthesize(role, 'test.Role').value() == 'method-role'
        metadata.synthesizeDeclared(role, 'test.Role').value() == 'method-role'
        metadata.synthesizeAll().find { it.annotationType().name == 'test.Role' }.value() == 'method-role'
        metadata.synthesizeDeclared().find { it.annotationType().name == 'test.Role' }.value() == 'method-role'
        metadata.declaredMetadata.synthesizeAll().find { it.annotationType().name == 'test.Role' }.value() == 'method-role'

        and: "synthesizing keeps one annotation per hierarchy layer, most specific first"
        metadata.synthesizeAll()*.annotationType()*.name ==
                ['io.micronaut.context.annotation.Executable', 'test.Role', 'test.Secure', 'jakarta.inject.Singleton', 'test.Role']
        metadata.synthesizeAll().findAll { it.annotationType().name == 'test.Role' }*.value() == ['method-role', 'class-role']

        cleanup:
        context.close()
    }
}
