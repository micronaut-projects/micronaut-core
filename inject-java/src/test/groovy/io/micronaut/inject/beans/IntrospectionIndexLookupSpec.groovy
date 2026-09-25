package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionProviders
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospectionsProvider
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.naming.NameUtils

class IntrospectionIndexLookupSpec extends AbstractTypeElementSpec {

    void 'constructor arguments and bean method arguments are returned by name'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('argget.Person', '''
package argget;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;

@Introspected
record Person(String name, int age) {
    @Executable
    String greet(String greeting) {
        return greeting + name;
    }
}
''')
        BeanMethod<?, ?> greet = introspection.beanMethods.find { it.name == 'greet' }

        expect:
        introspection.getConstructorArgument('name').get().is(introspection.constructorArguments[0])
        introspection.getConstructorArgument('age').get().type == int
        !introspection.getConstructorArgument('missing').isPresent()
        greet.getArgument('greeting').get().is(greet.arguments[0])
        !greet.getArgument('missing').isPresent()

        when:
        introspection.getConstructorArgument(null)

        then:
        thrown(NullPointerException)
    }

    void 'record properties, constructor arguments and methods are found by name'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('lookup.Person', '''
package lookup;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;

@Introspected
record Person(String name, int age, String city) {
    @Executable
    String greet(String greeting, String suffix) {
        return greeting + name + suffix;
    }
}
''')
        List<String> propertyNames = introspection.beanProperties*.name
        List<String> argumentNames = introspection.constructorArguments*.name
        BeanMethod<?, ?> greet = introspection.beanMethods.find { it.name == 'greet' }

        expect:
        propertyNames.every { introspection.propertyIndexOf(it) == propertyNames.indexOf(it) }
        argumentNames == ['name', 'age', 'city']
        argumentNames.every { introspection.constructorArgumentIndexOf(it) == argumentNames.indexOf(it) }
        introspection.propertyIndexOf('missing') == -1
        introspection.constructorArgumentIndexOf('missing') == -1
        greet.argumentIndexOf('greeting') == 0
        greet.argumentIndexOf('suffix') == 1
        greet.argumentIndexOf('missing') == -1
        introspection.getProperty('city').get().name == 'city'
    }

    void 'constructor argument indexes follow the constructor order, not the property order'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('lookup.Reordered', '''
package lookup;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Reordered {
    private final String alpha;
    private final String beta;

    Reordered(String beta, String alpha) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public String getAlpha() { return alpha; }
    public String getBeta() { return beta; }
}
''')

        expect:
        introspection.constructorArgumentIndexOf('beta') == 0
        introspection.constructorArgumentIndexOf('alpha') == 1
        introspection.propertyIndexOf('alpha') == introspection.beanProperties*.name.indexOf('alpha')
    }

    void 'builder-based introspections index the arguments getConstructorArguments returns'() {
        given:
        // getConstructorArguments() resolves builder-based introspections through
        // BeanIntrospection.getIntrospection(builderClass), which discovers generated
        // introspections by listing a META-INF/micronaut/ directory. This test harness compiles
        // and loads classes from an in-memory ClassLoader that cannot list directory resources,
        // so that discovery finds nothing here - a harness limitation, not a defect in the code
        // under test. Point the lookup at the introspections the annotation processor generated
        // for this compilation by their known naming convention (the same one buildBeanIntrospection
        // uses), only for this test's own ClassLoader, and restore the original provider after.
        ClassLoader classLoader = buildClassLoader('lookup.Built', '''
package lookup;

import io.micronaut.core.annotation.Introspected;

@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = Built.Builder.class))
class Built {
    private final String name;
    private final int age;

    private Built(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private int age;
        public Builder name(String name) { this.name = name; return this; }
        public Builder age(int age) { this.age = age; return this; }
        public Built build() { return new Built(name, age); }
    }
}
''')
        BeanIntrospectionsProvider originalProvider = BeanIntrospectionProviders.get()
        List<String> generatedIntrospectionNames = ['lookup.Built', 'lookup.Built$Builder'].collect { introspectionNameFor(it) }
        BeanIntrospectionProviders.set({ ClassLoader loader ->
            if (loader != classLoader) {
                return originalProvider.provide(loader)
            }
            return generatedIntrospectionNames.findResults { name ->
                try {
                    return (BeanIntrospectionReference<Object>) loader.loadClass(name).newInstance()
                } catch (ClassNotFoundException ignored) {
                    return null
                }
            }
        } as BeanIntrospectionsProvider)

        when:
        BeanIntrospection<?> introspection = (BeanIntrospection<?>) classLoader.loadClass(introspectionNameFor('lookup.Built')).newInstance()
        List<String> argumentNames = introspection.constructorArguments*.name

        then:
        argumentNames == ['name', 'age']
        argumentNames.every { introspection.constructorArgumentIndexOf(it) == argumentNames.indexOf(it) }
        introspection.constructorArgumentIndexOf('missing') == -1

        cleanup:
        BeanIntrospectionProviders.set(originalProvider)
    }

    void 'beans with more properties than the scan threshold are found by name'() {
        given:
        String fields = (0..<12).collect { "    private String p$it;\n    public String getP$it() { return p$it; }\n    public void setP$it(String v) { p$it = v; }" }.join('\n')
        BeanIntrospection<?> introspection = buildBeanIntrospection('lookup.Wide', """
package lookup;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Wide {
$fields
}
""")
        List<String> propertyNames = introspection.beanProperties*.name

        expect:
        propertyNames.size() == 12
        propertyNames.every { introspection.propertyIndexOf(it) == propertyNames.indexOf(it) }
        introspection.propertyIndexOf('p12') == -1
    }

    void 'null names are rejected'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('lookup.Simple', '''
package lookup;

import io.micronaut.core.annotation.Introspected;

@Introspected
record Simple(String name) {
}
''')

        when:
        introspection.constructorArgumentIndexOf(null)

        then:
        thrown(NullPointerException)

        when:
        introspection.propertyIndexOf(null)

        then:
        thrown(NullPointerException)
    }

    private static String introspectionNameFor(String beanClassName) {
        String simpleName = NameUtils.getSimpleName(beanClassName)
        String packageName = NameUtils.getPackageName(beanClassName)
        String prefix = simpleName.startsWith('$') ? '' : '$'
        return "${packageName}.${prefix}${simpleName}\$Introspection"
    }
}
