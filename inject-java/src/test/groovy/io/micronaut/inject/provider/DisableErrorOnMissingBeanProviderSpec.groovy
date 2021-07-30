package io.micronaut.inject.provider

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.Stepwise

@Stepwise
class DisableErrorOnMissingBeanProviderSpec extends AbstractTypeElementSpec {

    boolean shouldError = false

    void "test should error on missing bean provider"() {
        given:
        def context = buildContext('''
package missingprov;

import jakarta.inject.*;

@Singleton
class Test {
    @Inject
    Provider<String> stringProvider;
}
''')
        when:
        def bean = getBean(context, 'missingprov.Test')

        then:
        bean
        bean.stringProvider

        when:
        bean.stringProvider.get()

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }

    void "test default error on missing bean provider"() {
        given:
        shouldError = true
        def context = buildContext('''
package missingprov;

import jakarta.inject.*;

@Singleton
class Test {
    @Inject
    Provider<String> stringProvider;
}
''')
        when:
        def bean = getBean(context, 'missingprov.Test')

        then:
        thrown(BeanInstantiationException)

        cleanup:
        context.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.errorOnMissingBeanProvider(shouldError)
    }
}
