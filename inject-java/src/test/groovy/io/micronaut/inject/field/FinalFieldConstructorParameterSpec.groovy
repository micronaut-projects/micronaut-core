package io.micronaut.inject.field

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class FinalFieldConstructorParameterSpec extends AbstractTypeElementSpec {

    void "a final field whose constructor parameter carries the same #annotation is injected through the constructor"() {
        given: "the shape Lombok's @RequiredArgsConstructor generates with the annotation listed in lombok.copyableAnnotations"
        ApplicationContext context = buildContext("""\
package test;

@jakarta.inject.Singleton
class Test {
    $annotation
    private final String value;

    Test($annotation String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
""")

        when:
        def bean = getBean(context, 'test.Test')

        then:
        bean.value() == 'x'

        cleanup:
        context.close()

        where:
        annotation << [
            '@io.micronaut.context.annotation.Value("${foo.bar:x}")',
            '@io.micronaut.context.annotation.Property(name = "foo.bar", defaultValue = "x")'
        ]
    }

    void "a final field with @Inject whose constructor parameter has a different name still fails compilation"() {
        when:
        buildBeanDefinition('test.Test', '''\
package test;

@jakarta.inject.Singleton
class Test {
    @io.micronaut.context.annotation.Value("${foo.bar:x}")
    private final String value;

    Test(@io.micronaut.context.annotation.Value("${foo.bar:x}") String other) {
        this.value = other;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot inject into final field [value] of [test.Test]")
    }
}
