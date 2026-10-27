package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ExpressionsAwareArgument
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

class EnvironmentAnnotationMetadataDefaultsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Role(value = "${test.class-role}", scope = "class-scope")
@Tag("class-tag")
class Test {

    @Executable
    @Role("literal-role")
    void literal() {
    }

    @Executable
    @Role(value = "${test.method-role}", names = "${test.names}")
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

    String[] names() default {};

    String[] aliases() default {"${test.names}"};

    String unresolved() default "${test.missing}";
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
    String level() default "${test.missing-level}";
}
'''

    private static final Map<String, Object> PROPERTIES = [
            'test.class-role' : 'class-role',
            'test.method-role': 'method-role',
            'test.secure-role': 'secure-role',
            'test.scope'      : 'resolved-scope',
            'test.names'      : 'one,two',
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
        backing.getDefaultValues('test.Role').scope == '${test.scope}'
        metadata.getDefaultValues('test.Role').scope == 'resolved-scope'
        metadata.getDefaultValue('test.Role', 'scope', String).get() == 'resolved-scope'
        metadata.getDefaultValue('test.Role', 'value', String).isEmpty()
        metadata.getDefaultValue('test.Role', 'aliases', String[]).get() == ['one', 'two'] as String[]
        metadata.getDefaultValue('test.Role', 'aliases', String[]).get() == metadata.synthesize(context.classLoader.loadClass('test.Role')).aliases()
        metadata.isRepeatableAnnotation('test.Tag')
        metadata.findRepeatableAnnotation('test.Tag').get() == 'test.Tags'
        !metadata.isRepeatableAnnotation('test.Role')

        and: "synthesized annotations resolve placeholders"
        Class<? extends java.lang.annotation.Annotation> role = context.classLoader.loadClass('test.Role')
        metadata.synthesize(role).value() == 'method-role'
        metadata.synthesize(role).scope() == 'resolved-scope'
        metadata.synthesize(role).names() == ['one', 'two'] as String[]
        metadata.synthesize(role).names() == metadata.stringValues('test.Role', 'names')
        metadata.synthesize(role).hashCode() == synthesizedRole(role, 'method-role', 'resolved-scope', 'one', 'two').hashCode()
        metadata.synthesize(role) == synthesizedRole(role, 'method-role', 'resolved-scope', 'one', 'two')
        metadata.synthesizeDeclared(role).value() == 'method-role'
        metadata.synthesize(role, 'test.Role').value() == 'method-role'
        metadata.synthesizeDeclared(role, 'test.Role').value() == 'method-role'
        metadata.synthesizeAll().find { it.annotationType().name == 'test.Role' }.value() == 'method-role'
        metadata.synthesizeDeclared().find { it.annotationType().name == 'test.Role' }.value() == 'method-role'
        metadata.declaredMetadata.synthesizeAll().find { it.annotationType().name == 'test.Role' }.value() == 'method-role'

        and: "the members of a synthesized annotation are the ones of the most specific element, not merged with the class"
        metadata.findAnnotation('test.Role').get().stringValue('scope').get() == 'class-scope'
        metadata.synthesize(role).scope() == 'resolved-scope'
        metadata.synthesizeAll().find { it.annotationType().name == 'test.Role' && it.value() == 'class-role' }.scope() == 'class-scope'

        and: "the declared metadata without property expressions still resolves the placeholder defaults"
        AnnotationMetadata literal = definition.getRequiredMethod('literal').annotationMetadata
        literal instanceof AbstractEnvironmentAnnotationMetadata
        literal.declaredMetadata.synthesize(role).value() == 'literal-role'
        literal.declaredMetadata.synthesize(role).scope() == 'resolved-scope'

        and: "synthesizing keeps one annotation per hierarchy layer, most specific first"
        metadata.synthesizeAll()*.annotationType()*.name.sort() ==
                ['io.micronaut.context.annotation.Executable', 'jakarta.inject.Singleton', 'test.Role', 'test.Role', 'test.Secure']
        metadata.synthesizeAll().findAll { it.annotationType().name == 'test.Role' }*.value() == ['method-role', 'class-role']

        and: "the declared annotations are the ones of the declared metadata, without the class annotations"
        metadata.synthesizeDeclared().findAll { it.annotationType().name == 'test.Role' }*.value() == ['method-role']
        !metadata.synthesizeDeclared().any { it.annotationType().name == 'jakarta.inject.Singleton' }
        metadata.synthesizeDeclared()*.annotationType()*.name as Set ==
                metadata.declaredMetadata.synthesizeAll()*.annotationType()*.name as Set

        and: "the declared metadata and the synthesized annotations that do not depend on the environment are cached"
        Class<? extends java.lang.annotation.Annotation> executable = io.micronaut.context.annotation.Executable
        // a default with a placeholder that cannot be resolved yet depends on the environment too
        Class<? extends java.lang.annotation.Annotation> secure = context.classLoader.loadClass('test.Secure')
        metadata.declaredMetadata.is(metadata.declaredMetadata)
        metadata.synthesize(executable).is(metadata.synthesize(executable))
        !metadata.synthesize(secure).is(metadata.synthesize(secure))
        metadata.synthesizeDeclared(executable).is(metadata.synthesizeDeclared(executable))

        and: "the synthesized annotations that depend on the environment follow its changes"
        !metadata.synthesize(role).is(metadata.synthesize(role))
        !metadata.synthesizeAll().is(metadata.synthesizeAll())

        when:
        context.environment.addPropertySource(io.micronaut.context.env.PropertySource.of('changed', ['test.method-role': 'changed-role']))

        then:
        metadata.stringValue('test.Role').get() == 'changed-role'
        metadata.synthesize(role).value() == 'changed-role'
        metadata.synthesizeAll().find { it.annotationType().name == 'test.Role' }.value() == 'changed-role'

        when: "a default placeholder that could not be resolved gets its property"
        context.environment.addPropertySource(io.micronaut.context.env.PropertySource.of('level', ['test.missing-level': 'high']))

        then:
        metadata.synthesize(secure).level() == 'high'

        cleanup:
        context.close()
    }

    void "test the declared metadata of environment aware bean, injection point and argument metadata"() {
        given:
        ApplicationContext context = buildContext('test.Bean', '''
package test;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Singleton
@Role("${test.class-role}")
class Bean extends Base {

    @Inject
    @Role("${test.field-role}")
    Dep field;

    Bean(@Role("${test.argument-role}") Dep dep) {
    }
}

@Mark("${test.inherited-mark}")
class Base {
}

@Singleton
class Dep {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@interface Role {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@interface Mark {
    String value();
}
''', false, [
                'test.class-role'    : 'class-role',
                'test.field-role'    : 'field-role',
                'test.argument-role' : 'argument-role',
                'test.inherited-mark': 'inherited-mark',
        ])
        BeanDefinition<?> definition = context.getBeanDefinition(context.classLoader.loadClass('test.Bean'))
        AnnotationMetadata beanMetadata = definition.annotationMetadata
        AnnotationMetadata fieldMetadata = definition.injectedFields.first().annotationMetadata
        // the context wraps the arguments with property expressions when it resolves them
        AnnotationMetadata argumentMetadata = ExpressionsAwareArgument.wrapIfNecessary(definition.constructor.arguments.first(), context, definition).annotationMetadata

        expect: "the bean metadata includes the inherited annotation, the declared metadata excludes it"
        beanMetadata instanceof AbstractEnvironmentAnnotationMetadata
        beanMetadata.stringValue('test.Mark').get() == 'inherited-mark'
        beanMetadata.declaredMetadata.stringValue('test.Role').get() == 'class-role'
        !beanMetadata.declaredMetadata.hasAnnotation('test.Mark')

        and: "the injection point and argument declared metadata resolve placeholders"
        fieldMetadata instanceof AbstractEnvironmentAnnotationMetadata
        fieldMetadata.declaredMetadata.stringValue('test.Role').get() == 'field-role'
        argumentMetadata instanceof AbstractEnvironmentAnnotationMetadata
        argumentMetadata.declaredMetadata.stringValue('test.Role').get() == 'argument-role'

        cleanup:
        context.close()
    }

    private static java.lang.annotation.Annotation synthesizedRole(Class<? extends java.lang.annotation.Annotation> role,
                                                                   String value,
                                                                   String scope,
                                                                   String... names) {
        AnnotationMetadataSupport.buildAnnotation(role, AnnotationValue.builder(role).value(value).member('scope', scope).member('names', names).member('aliases', names).build())
    }

    void "test the declared metadata keeps every layer of a hierarchy that delegates declared to all elements"() {
        given:
        ApplicationContext context = ApplicationContext.run(['test.first': 'first', 'test.second': 'second'])
        MutableAnnotationMetadata first = new MutableAnnotationMetadata()
        first.addDeclaredAnnotation('test.First', [value: '${test.first}'])
        MutableAnnotationMetadata second = new MutableAnnotationMetadata()
        second.addDeclaredAnnotation('test.Second', [value: '${test.second}'])
        AnnotationMetadata metadata = new AbstractEnvironmentAnnotationMetadata(new AnnotationMetadataHierarchy(true, first, second)) {
            @Override
            protected io.micronaut.context.env.Environment getEnvironment() {
                return context.environment
            }
        }

        expect:
        metadata.declaredAnnotationNames == ['test.First', 'test.Second'] as Set
        metadata.declaredMetadata.is(metadata)
        metadata.declaredMetadata.stringValue('test.First').get() == 'first'
        metadata.declaredMetadata.stringValue('test.Second').get() == 'second'

        cleanup:
        context.close()
    }
}
