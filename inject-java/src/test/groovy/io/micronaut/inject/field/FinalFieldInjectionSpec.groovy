package io.micronaut.inject.field

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class FinalFieldInjectionSpec extends AbstractTypeElementSpec {

    void "test @Inject on a final field fails compilation"() {
        when:
        buildBeanDefinition('test.Test', '''\
package test;

@jakarta.inject.Singleton
class Test {
    @jakarta.inject.Inject
    final Dep dep = null;
}

@jakarta.inject.Singleton
class Dep {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot inject into final field [dep] of [test.Test]: use constructor injection or remove the final modifier")
    }

    void "test #annotation on a final field fails compilation"() {
        when:
        buildBeanDefinition('test.Test', """\
package test;

@jakarta.inject.Singleton
class Test {
    $annotation
    final String value = null;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot inject into final field [value] of [test.Test]: use constructor injection or remove the final modifier")

        where:
        annotation << [
            '@io.micronaut.context.annotation.Value("${foo.bar:x}")',
            '@io.micronaut.context.annotation.Property(name = "foo.bar")'
        ]
    }

    void "test a record component with @Value is still injected through the constructor"() {
        when:
        def definition = buildBeanDefinition('test.Test', '''\
package test;

@jakarta.inject.Singleton
record Test(@io.micronaut.context.annotation.Value("${foo.bar:x}") String value) {
}
''')

        then:
        definition != null
    }
}
