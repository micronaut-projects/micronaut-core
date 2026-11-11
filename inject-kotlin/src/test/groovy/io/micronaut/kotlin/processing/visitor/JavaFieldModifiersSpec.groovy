package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement

class JavaFieldModifiersSpec extends AbstractKotlinCompilerSpec {

    private static final String JAVA_SOURCE = '''
package test;

public class JavaFields {
    public static final String CONSTANT = "c";
    public static String staticField;
    public String publicField;
    public final String publicFinalField = "f";
    protected String protectedField;
    String packagePrivateField;
    private String privateField;
    public transient String transientField;
    public volatile String volatileField;
}
'''

    // what inject-java reports for the same fields
    private static final Map<String, String> EXPECTED = [
            CONSTANT           : 'final public static [FINAL, PUBLIC, STATIC]',
            staticField        : 'public static [PUBLIC, STATIC]',
            publicField        : 'public [PUBLIC]',
            publicFinalField   : 'final public [FINAL, PUBLIC]',
            protectedField     : 'protected [PROTECTED]',
            packagePrivateField: 'package-private []',
            privateField       : 'private [PRIVATE]',
            transientField     : 'public [PUBLIC, TRANSIENT]',
            volatileField      : 'public [PUBLIC, VOLATILE]',
    ]

    void "test the modifiers of the fields of a Java source"() {
        given:
        Map<String, String> fields = null

        when:
        KotlinCompiler.compile([KotlinCompiler.javaSource('JavaFields.java', JAVA_SOURCE)], { ClassElement ce ->
            if (ce.name == 'test.JavaFields') {
                fields = describeFields(ce)
            }
        }, [])

        then:
        fields == EXPECTED
    }

    void "test the modifiers of the fields of a Java class compiled in the same compilation as Kotlin"() {
        given:
        Map<String, String> fields = null

        when:
        KotlinCompiler.compile([
                KotlinCompiler.javaSource('JavaFields.java', JAVA_SOURCE),
                KotlinCompiler.kotlinSource('Sub.kt', '''
package test

class Sub : JavaFields()
''')
        ], { ClassElement ce ->
            if (ce.name == 'test.Sub') {
                fields = describeFields(ce.superType.get())
            }
        }, [])

        then:
        fields == EXPECTED
    }

    void "test the modifiers of the fields of a compiled Java class"() {
        when:
        Map<String, String> fields = buildClassElementMapped('test.Sub', '''
package test

class Sub : io.micronaut.kotlin.processing.visitor.JavaFieldModifiers()
''') { ce ->
            describeFields(ce.superType.get())
        }

        then:
        fields == EXPECTED
    }

    void "test the accessibility of the fields of a Java class"() {
        when:
        Map<String, Boolean> reflection = buildClassElementMapped('test.Sub', '''
package test

class Sub : io.micronaut.kotlin.processing.visitor.JavaFieldModifiers()
''') { ce ->
            ce.superType.get().getEnclosedElements(ElementQuery.ALL_FIELDS.includeHiddenElements()).collectEntries { FieldElement f ->
                [f.name, f.isReflectionRequired(ce)]
            }
        }

        then: "only the public fields can be accessed without reflection from another package"
        reflection == [
                CONSTANT           : false,
                staticField        : false,
                publicField        : false,
                publicFinalField   : false,
                protectedField     : true,
                packagePrivateField: true,
                privateField       : true,
                transientField     : false,
                volatileField      : false,
        ]
    }

    void "test a Kotlin bean injects the public field of a compiled Java superclass"() {
        given:
        def context = buildContext('''
package test

import jakarta.inject.Singleton

@Singleton
class InjectedBean : io.micronaut.kotlin.processing.visitor.JavaInjectedFieldBase()
''')

        when:
        def bean = getBean(context, 'test.InjectedBean')

        then:
        bean.beanContext.is(context)

        cleanup:
        context.close()
    }

    void "test a Kotlin introspection reads and writes the public fields of a compiled Java superclass through the fields"() {
        when:
        def introspection = buildBeanIntrospection('test.Introspected', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected(accessKind = [Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD])
class Introspected : io.micronaut.kotlin.processing.visitor.JavaFieldModifiers()
''')

        def bean = introspection.instantiate()
        def publicField = introspection.getRequiredProperty('publicField', String)
        publicField.set(bean, 'written')

        then:
        introspection.propertyNames.toList().sort() == ['publicField', 'publicFinalField', 'transientField', 'volatileField']
        !publicField.readOnly
        bean.publicField == 'written'
        publicField.get(bean) == 'written'
        introspection.getRequiredProperty('publicFinalField', String).readOnly
        introspection.getRequiredProperty('publicFinalField', String).get(bean) == 'f'
        !introspection.getRequiredProperty('volatileField', String).readOnly
    }

    private static Map<String, String> describeFields(ClassElement ce) {
        ce.getEnclosedElements(ElementQuery.ALL_FIELDS.includeHiddenElements()).collectEntries { FieldElement f ->
            List<String> flags = []
            if (f.isFinal()) flags << 'final'
            if (f.isPublic()) flags << 'public'
            if (f.isProtected()) flags << 'protected'
            if (f.isPackagePrivate()) flags << 'package-private'
            if (f.isPrivate()) flags << 'private'
            if (f.isStatic()) flags << 'static'
            [f.name, flags.join(' ') + ' ' + f.modifiers*.name().sort().toString()]
        }
    }
}
