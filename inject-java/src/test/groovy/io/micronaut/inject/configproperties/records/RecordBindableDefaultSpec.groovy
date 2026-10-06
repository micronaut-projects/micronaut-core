package io.micronaut.inject.configproperties.records

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import java.time.Duration

class RecordBindableDefaultSpec extends AbstractTypeElementSpec {

    void "test a @Bindable default applies to a nullable record component"() {
        given:
        def context = buildContext('test.NullableDefaultsConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigurationProperties("test")
record NullableDefaultsConfig(
    @Bindable(defaultValue = "2s") @Nullable Duration warnWait,
    @Bindable(defaultValue = "abc") @Nullable String name,
    @Nullable String other,
    @Bindable(defaultValue = "0") int size
) {
}
''', false, ['test.size': 3])

        when:
        def config = getBean(context, 'test.NullableDefaultsConfig')

        then:
        config.warnWait() == Duration.ofSeconds(2)
        config.name() == 'abc'
        config.other() == null
        config.size() == 3

        cleanup:
        context.close()
    }

    void "test a configured value replaces the @Bindable default of a nullable record component"() {
        given:
        def context = buildContext('test.NullableDefaultsConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigurationProperties("test")
record NullableDefaultsConfig(@Bindable(defaultValue = "2s") @Nullable Duration warnWait) {
}
''', false, ['test.warn-wait': '5s'])

        when:
        def config = getBean(context, 'test.NullableDefaultsConfig')

        then:
        config.warnWait() == Duration.ofSeconds(5)

        cleanup:
        context.close()
    }

    void "test a @Bindable default applies to a nullable constructor parameter of a configuration class"() {
        given:
        def context = buildContext('test.NullableDefaultsConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigurationProperties("test")
class NullableDefaultsConfig {
    private final @Nullable Duration warnWait;

    @ConfigurationInject
    NullableDefaultsConfig(@Bindable(defaultValue = "2s") @Nullable Duration warnWait) {
        this.warnWait = warnWait;
    }

    public @Nullable Duration getWarnWait() {
        return warnWait;
    }
}
''', false, [:])

        when:
        def config = getBean(context, 'test.NullableDefaultsConfig')

        then:
        config.warnWait == Duration.ofSeconds(2)

        cleanup:
        context.close()
    }
}
