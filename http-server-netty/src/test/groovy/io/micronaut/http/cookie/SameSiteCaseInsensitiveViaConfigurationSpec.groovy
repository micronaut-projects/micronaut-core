package io.micronaut.http.cookie

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import spock.lang.Specification
import spock.lang.Unroll

class SameSiteCaseInsensitiveViaConfigurationSpec extends Specification {

    @Unroll
    void "SameSite allows for case insensitive configuration binding"(String sameSiteValue, SameSite expected) {
        given:
        ApplicationContext context = ApplicationContext.run([
                "spec.name": "SameSiteCaseInsensitiveViaConfigurationSpec",
                "mock.same-site": sameSiteValue,
        ])
        MockConfigurationProperties props = context.getBean(MockConfigurationProperties)

        expect:
        expected == props.sameSite

        cleanup:
        context.close()

        where:
        sameSiteValue || expected
        'Strict'      || SameSite.Strict
        'strict'      || SameSite.Strict
        'sTrict'      || SameSite.Strict
        'bogus'       || null

    }

    @Requires(property = "spec.name", value = "SameSiteCaseInsensitiveViaConfigurationSpec")
    @ConfigurationProperties("mock")
    static class MockConfigurationProperties {
        @Nullable
        SameSite sameSite
    }

}
