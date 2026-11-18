package io.micronaut.inject.any.qualifier

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.DefaultBeanResolutionContext
import io.micronaut.context.Qualifier
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.type.Argument
import io.micronaut.inject.DelegatingBeanDefinition
import io.micronaut.inject.qualifiers.AnyQualifier
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
        def abc = context.getQualifiedBeanRegistration(definition, type, ABC).bean
        def xyz = context.getQualifiedBeanRegistration(definition, type, XYZ).bean

        then:
        abc.name == "ABC"
        xyz.name == "XYZ"
        !abc.is(xyz)

        and: "the singleton is the one a lookup with the same qualifier finds"
        abc.is(context.getBean(MyCustomBean, ABC))
        xyz.is(context.getBean(MyCustomBean, XYZ))
        context.getQualifiedBeanRegistration(definition, type, ABC).bean.is(abc)
    }

    void 'a resolution context resolves an @Any definition for each qualifier'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean2, Qualifiers.byName("ABC"))
        def type = Argument.of(MyCustomBean2)
        def resolution = new DefaultBeanResolutionContext(context, null)

        when:
        def abc = resolution.getQualifiedBean(definition, type, Qualifiers.byName("ABC"))
        def xyz = resolution.getQualifiedBeanRegistration(definition, type, Qualifiers.byName("XYZ")).bean

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

        expect: "the definition is the any factory's, qualified by the name, which the resolver qualifies again"
        definition instanceof DelegatingBeanDefinition
        definition.declaredQualifier == ABC
        (definition as DelegatingBeanDefinition).target.declaredQualifier == AnyQualifier.INSTANCE

        when:
        def abc = group.getQualifiedBean(definition, type, ABC)
        def xyz = group.getQualifiedBeanRegistration(definition, type, XYZ).bean

        then:
        abc.name == "ABC"
        xyz.name == "XYZ"
        !abc.is(xyz)
        abc.is(context.getBean(MyCustomBean, ABC))
        xyz.is(context.getBean(MyCustomBean, XYZ))

        cleanup:
        group?.close()
    }

    void 'a resolution context resolves an @Any definition for each qualifier at a lookup point'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean, ABC)
        def type = Argument.of(MyCustomBean)
        def byDef = Qualifiers.<MyCustomBean> byName("DEF")
        def byGhi = Qualifiers.<MyCustomBean> byName("GHI")
        def resolution = new DefaultBeanResolutionContext(context, null)

        when:
        def first = resolution.getQualifiedBeanRegistration(definition, type, byDef, lookupPoint("first")).bean
        def second = resolution.getQualifiedBeanRegistration(definition, type, byGhi, lookupPoint("second")).bean

        then:
        first.name == "DEF"
        second.name == "GHI"
        !first.is(second)
        first.is(context.getBean(MyCustomBean, byDef))
        second.is(context.getBean(MyCustomBean, byGhi))

        and: "the path is as it was once the resolution returns"
        resolution.path.isEmpty()

        cleanup:
        resolution?.close()
    }

    void 'a dependency resolver resolves an @Any definition for each qualifier at a lookup point'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean2, ABC)
        def type = Argument.of(MyCustomBean2)
        def group = context.createDependencyGroup()

        when:
        def first = group.getQualifiedBeanRegistration(definition, type, Qualifiers.byName("JKL"), lookupPoint("first")).bean
        def second = group.getQualifiedBeanRegistration(definition, type, Qualifiers.byName("MNO"), lookupPoint("second")).bean

        then:
        first.name == "JKL"
        second.name == "MNO"
        !first.is(second)

        cleanup:
        group?.close()
    }

    void 'the qualified forms require a qualifier'() {
        given:
        def definition = context.getBeanDefinition(PlainBean)
        def type = Argument.of(PlainBean)
        def resolution = new DefaultBeanResolutionContext(context, null)
        def group = context.createDependencyGroup()

        when:
        context.getQualifiedBeanRegistration(definition, type, null)

        then:
        thrown(NullPointerException)

        when:
        resolution.getQualifiedBean(definition, type, null)

        then:
        thrown(NullPointerException)

        when:
        resolution.getQualifiedBeanRegistration(definition, type, null, lookupPoint("point"))

        then:
        thrown(NullPointerException)

        when:
        group.getQualifiedBeanRegistration(definition, type, null, lookupPoint("point"))

        then:
        thrown(NullPointerException)

        cleanup:
        resolution?.close()
        group?.close()
    }

    void 'without a qualifier an @Any singleton definition is the bean a lookup without a qualifier finds'() {
        given:
        def definition = context.getBeanDefinition(MyAnyEngine)
        def type = Argument.of(MyAnyEngine)
        def resolution = new DefaultBeanResolutionContext(context, null)
        def group = context.createDependencyGroup()

        when:
        def plain = context.getBean(MyAnyEngine)

        then: "the factory is given no qualifier, as for the lookup"
        plain.name == null

        and: "every form that resolves the chosen definition without a qualifier returns the same singleton"
        context.getBeanRegistration(definition, type).bean.is(plain)
        context.getBean(definition).is(plain)
        context.getBeanRegistration(definition).bean.is(plain)
        resolution.getBean(definition, type).is(plain)
        group.getBean(definition, type).is(plain)

        when: "a qualifier still gets its own instance, which a lookup with it finds"
        def named = context.getQualifiedBeanRegistration(definition, type, Qualifiers.byName("named")).bean

        then:
        named.name == "named"
        !named.is(plain)
        named.is(context.getBean(MyAnyEngine, Qualifiers.byName("named")))

        cleanup:
        resolution?.close()
        group?.close()
    }

    void 'without a qualifier an @Any prototype definition is created for no qualifier'() {
        given:
        def definition = context.getBeanDefinition(MyCustomBean2, Qualifiers.byName("ABC"))

        when: "the factory is given no qualifier, as for a lookup without one, and cannot read a name from it"
        context.getBeanRegistration(definition, Argument.of(MyCustomBean2))

        then:
        thrown(BeanInstantiationException)
    }

    void 'a definition that is not of any qualifier keeps its declared qualifier'() {
        given:
        def definition = context.getBeanDefinition(PlainBean)

        when:
        def bean = context.getQualifiedBeanRegistration(definition, Argument.of(PlainBean), Qualifiers.byName("other")).bean

        then:
        bean.is(context.getBean(PlainBean))
    }

    private BeanResolutionContext.Segment<PlainBean, Object> lookupPoint(String name) {
        def declaring = context.getBeanDefinition(PlainBean)
        return [
            getDeclaringType         : { -> declaring },
            getDeclaringTypeQualifier: { -> null },
            getInjectionPoint        : { -> null },
            getName                  : { -> name },
            getArgument              : { -> Argument.OBJECT_ARGUMENT },
        ] as BeanResolutionContext.Segment<PlainBean, Object>
    }
}
