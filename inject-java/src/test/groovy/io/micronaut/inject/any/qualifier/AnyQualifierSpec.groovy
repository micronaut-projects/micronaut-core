package io.micronaut.inject.any.qualifier

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AnyQualifierSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(['spec.name':'AnyQualifierSpec'])

    void 'test singleton any injection'() {
        when:
            def abc = context.getBean(MyCustomBean, Qualifiers.byName("ABC"))
            def xyz = context.getBean(MyCustomBean, Qualifiers.byName("XYZ"))
        then:
            abc.name == "ABC"
            xyz.name == "XYZ"
        when:
            def abc2 = context.getBean(MyCustomBean, Qualifiers.byName("ABC"))
            def xyz2 = context.getBean(MyCustomBean, Qualifiers.byName("XYZ"))
        then:
            abc == abc2
            xyz == xyz2
    }

    void 'test prototype any injection'() {
        when:
            def abc = context.getBean(MyCustomBean2, Qualifiers.byName("ABC"))
            def xyz = context.getBean(MyCustomBean2, Qualifiers.byName("XYZ"))
        then:
            abc.name == "ABC"
            xyz.name == "XYZ"
        when:
            def abc2 = context.getBean(MyCustomBean2, Qualifiers.byName("ABC"))
            def xyz2 = context.getBean(MyCustomBean2, Qualifiers.byName("XYZ"))
        then:
            abc2.name == "ABC"
            xyz2.name == "XYZ"
            abc != abc2
            xyz != xyz2
    }
}
