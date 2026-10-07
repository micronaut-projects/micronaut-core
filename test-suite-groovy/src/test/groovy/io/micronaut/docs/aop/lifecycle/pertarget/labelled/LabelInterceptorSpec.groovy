package io.micronaut.docs.aop.lifecycle.pertarget.labelled

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class LabelInterceptorSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
        'labelled-channels.sms.sender': 'Fred',
        'labelled-channels.email.sender': 'Bob')

    void 'test each target is labelled with its qualifier'() {
        // tag::test[]
        when:
        Channel sms = context.getBean(Channel, Qualifiers.byName('sms'))
        Channel email = context.getBean(Channel, Qualifiers.byName('email'))

        then:
        sms.send('Hello') == '[sms] Hello from Fred'
        email.send('Hello') == '[email] Hello from Bob'
        // end::test[]
    }
}
