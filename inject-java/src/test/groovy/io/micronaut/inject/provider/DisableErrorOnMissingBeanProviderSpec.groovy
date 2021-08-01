package io.micronaut.inject.provider

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.Stepwise

@Stepwise
class DisableErrorOnMissingBeanProviderSpec extends AbstractTypeElementSpec {

    boolean allowEmptyProviders = true

    void "test should not error on missing bean provider if disabled"() {
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

    void "test should not error on missing bean provider with qualifier if disabled"() {
        given:
        def context = buildContext('''
package missingprov;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.inject.*;

@Singleton
class Test {
    @Inject
    @MyQualifier
    Provider<String> stringProvider;
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface MyQualifier {}
''')
        when:
        def bean = getBean(context, 'missingprov.Test')

        then:
        bean
        bean.stringProvider

        when:
        bean.stringProvider.get()

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains("No bean of type [java.lang.String] exists for the given qualifier: @MyQualifier")

        cleanup:
        context.close()
    }

    void "test should not error on missing bean provider with qualifier with annotation members if disabled"() {
        given:
        def context = buildContext('''
package missingprov;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.inject.*;

@Singleton
class Test {
    @Inject
    @MyQualifier(name="Bob")
    Provider<String> stringProvider;
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface MyQualifier {
    String name();
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
        def e = thrown(NoSuchBeanException)
        e.message.contains("No bean of type [java.lang.String] exists for the given qualifier: @MyQualifier")

        cleanup:
        context.close()
    }

    void "test default error on missing bean provider"() {
        given:
        allowEmptyProviders = false
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
        contextBuilder.allowEmptyProviders(allowEmptyProviders)
    }
}
