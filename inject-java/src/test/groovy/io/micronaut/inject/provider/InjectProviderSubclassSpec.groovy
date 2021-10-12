package io.micronaut.inject.provider

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InjectProviderSubclassSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test you can lookup a provider impl'() {
        when:
        StringProviderReceiver sp = context.getBean(StringProviderReceiver)

        then:
        sp.get() == 'hello world'
    }
}
