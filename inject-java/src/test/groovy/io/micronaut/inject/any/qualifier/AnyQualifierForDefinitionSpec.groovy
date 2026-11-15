package io.micronaut.inject.any.qualifier

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanResolutionContext
import io.micronaut.context.Qualifier
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A caller that has already chosen the definition of an {@code @Any} factory resolves it for a qualifier, as a
 * lookup with that qualifier would: the factory sees that qualifier, and the scope holds the bean under it.
 */
class AnyQualifierForDefinitionSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(['spec.name': 'AnyQualifierSpec'])

    private static final Qualifier<MyCustomBean> ABC = Qualifiers.byName("ABC")
    private static final Qualifier<MyCustomBean> XYZ = Qualifiers.byName("XYZ")

    void 'the bean context resolves an @Any singleton definition for each qualifier'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean, ABC)
        def type = Argument.of(MyCustomBean)

        when:
        def abc = context.getBeanRegistration(definition, type, ABC).bean
        def xyz = context.getBeanRegistration(definition, type, XYZ).bean

        then:
        abc.name == "ABC"
        xyz.name == "XYZ"
        !abc.is(xyz)

        and: "the singleton is the one a lookup with the same qualifier finds"
        abc.is(context.getBean(MyCustomBean, ABC))
        xyz.is(context.getBean(MyCustomBean, XYZ))
        context.getBeanRegistration(definition, type, ABC).bean.is(abc)
    }

    void 'a resolution context resolves an @Any definition for each qualifier'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean2, Qualifiers.byName("ABC"))
        def type = Argument.of(MyCustomBean2)
        def resolution = new DefaultBeanResolutionContext(context, null)

        when:
        def abc = resolution.getBean(definition, type, Qualifiers.byName("ABC"))
        def xyz = resolution.getBeanRegistration(definition, type, Qualifiers.byName("XYZ")).bean

        then:
        abc.name == "ABC"
        xyz.name == "XYZ"
        !abc.is(xyz)

        cleanup:
        resolution?.close()
    }

    void 'a dependency resolver resolves an @Any definition for each qualifier'() {
        given: "after a lookup by the name, the definition found by it is the one qualified by it"
        context.getBean(MyCustomBean, ABC)
        def definition = context.getBeanDefinition(MyCustomBean, ABC)
        def type = Argument.of(MyCustomBean)
        def group = context.createDependencyGroup()

        when:
        def abc = group.getBean(definition, type, ABC)
        def xyz = group.getBeanRegistration(definition, type, XYZ).bean

        then:
        abc.name == "ABC"
        xyz.name == "XYZ"
        !abc.is(xyz)
        abc.is(context.getBean(MyCustomBean, ABC))
        xyz.is(context.getBean(MyCustomBean, XYZ))

        cleanup:
        group?.close()
    }

    void 'without a qualifier an @Any definition is resolved for its declared qualifier'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean2, Qualifiers.byName("ABC"))

        when: "the factory is given the any qualifier, which it cannot read a name from"
        context.getBeanRegistration(definition, Argument.of(MyCustomBean2), (Qualifier<MyCustomBean2>) null)

        then:
        thrown(BeanInstantiationException)
    }

    void 'a definition that is not of any qualifier keeps its declared qualifier'() {
        given:
        def definition = context.getBeanDefinition(PlainBean)

        when:
        def bean = context.getBeanRegistration(definition, Argument.of(PlainBean), Qualifiers.byName("other")).bean

        then:
        bean.is(context.getBean(PlainBean))
    }
}
