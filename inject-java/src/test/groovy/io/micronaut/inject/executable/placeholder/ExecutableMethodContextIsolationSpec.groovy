package io.micronaut.inject.executable.placeholder

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class ExecutableMethodContextIsolationSpec extends Specification {

    void "a placeholder resolves in a context started after an earlier one stopped"() {
        given:
        ApplicationContext first = run('one')

        expect:
        tag(first, 'placeholder') == 'one'
        first.getBean(TaggedProcessor).seen.toSorted() == ['one', 'one']

        when:
        first.close()
        ApplicationContext second = run('two')

        then:
        tag(second, 'placeholder') == 'two'
        second.getBean(TaggedProcessor).seen.toSorted() == ['two', 'two']

        cleanup:
        second?.close()
    }

    void "contexts running side by side resolve their own placeholders and expressions"() {
        given:
        ApplicationContext first = run('one')
        ApplicationContext second = run('two')

        expect:
        tag(first, method) == 'one'
        tag(second, method) == 'two'
        tag(first, method) == 'one'

        cleanup:
        first.close()
        second.close()

        where:
        method << ['placeholder', 'expression']
    }

    void "the return type metadata of a method follows its own context"() {
        given:
        ApplicationContext first = run('one')
        ApplicationContext second = run('two')

        expect:
        method(first, 'placeholder').returnType.annotationMetadata.stringValue(Tagged).get() == 'one'
        method(second, 'placeholder').returnType.annotationMetadata.stringValue(Tagged).get() == 'two'

        cleanup:
        first.close()
        second.close()
    }

    void "a definition keeps its executable methods for the life of the context"() {
        given:
        ApplicationContext context = run('one')

        expect:
        method(context, 'placeholder').is(method(context, 'placeholder'))

        cleanup:
        context.close()
    }

    private static ApplicationContext run(String tag) {
        ApplicationContext.run('spec.name': 'ExecutableMethodContextIsolationSpec', 'app.tag': tag)
    }

    private static String tag(ApplicationContext context, String name) {
        method(context, name).stringValue(Tagged).orElse(null)
    }

    private static ExecutableMethod<?, ?> method(ApplicationContext context, String name) {
        context.getBeanDefinition(TaggedService).getRequiredMethod(name)
    }
}
