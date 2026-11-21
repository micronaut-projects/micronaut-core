package io.micronaut.inject.any.qualifier

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.DefaultBeanContext
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

import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

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
        resolution.getBeanRegistration(definition, type, lookupPoint("resolution")).bean.is(plain)
        group.getBeanRegistration(definition, type, lookupPoint("group")).bean.is(plain)

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
        def definition = context.getBeanDefinition(MyAnyPrototype)
        def type = Argument.of(MyAnyPrototype)

        when:
        def first = context.getBeanRegistration(definition, type).bean
        def second = context.getBean(definition)

        then: "the factory is given no qualifier, as for a lookup without one"
        first.qualifier == null
        second.qualifier == null
        !first.is(second)
        context.getBean(MyAnyPrototype).qualifier == null

        and: "a qualifier is given to the factory"
        context.getQualifiedBeanRegistration(definition, type, Qualifiers.byName("named")).bean.qualifier == Qualifiers.byName("named")
    }

    void 'the delegate a lookup by the any qualifier made is resolved without a qualifier as the definition of any qualifier'() {
        given: "the definition found after a lookup by the any qualifier, which the lookup qualified"
        def anyQualified = context.getBean(MyAnyEngine, Qualifiers.any())
        def definition = context.getBeanDefinition(MyAnyEngine, Qualifiers.any())
        def type = Argument.of(MyAnyEngine)
        def resolution = new DefaultBeanResolutionContext(context, null)
        def group = context.createDependencyGroup()

        expect:
        definition instanceof DelegatingBeanDefinition
        definition.declaredQualifier == AnyQualifier.INSTANCE

        when:
        def plain = context.getBean(MyAnyEngine)

        then: "every form that resolves it without a qualifier returns the singleton of the lookup without one"
        !plain.is(anyQualified)
        context.getBeanRegistration(definition, type).bean.is(plain)
        context.getBean(definition).is(plain)
        context.getBeanRegistration(definition).bean.is(plain)
        resolution.getBean(definition, type).is(plain)
        group.getBean(definition, type).is(plain)

        and: "the any qualifier still resolves it to the bean of the lookup by it"
        context.getQualifiedBeanRegistration(definition, type, Qualifiers.any()).bean.is(anyQualified)

        cleanup:
        resolution?.close()
        group?.close()
    }

    void 'an @EachBean of an @Any bean is resolved by its definition as a lookup without a qualifier resolves it'() {
        given:
        def definition = context.getBeanDefinitions(MyAnyEngineChild).first()
        def anyDefinition = context.getBeanDefinitions(MyAnyEachBean).first()

        when:
        def child = context.getBean(definition)
        def anyChild = context.getBean(anyDefinition)

        then: "the singleton is the one of the lookup, and its factory sees no qualifier, as for the lookup"
        child.is(context.getBean(MyAnyEngineChild))
        child.qualifier() == null

        and: "an @EachBean declared @Any keeps the bean it is created for"
        anyChild.is(context.getBean(MyAnyEachBean))
        anyChild.engine != null
    }

    void 'recreating the singleton of a lookup by the any qualifier creates it again for that qualifier'() {
        given:
        def recreating = ApplicationContext.run(['spec.name': 'AnyQualifierSpec'])
        def plain = recreating.getBean(MyAnyEngine)
        def anyQualified = recreating.getBean(MyAnyEngine, Qualifiers.any())

        expect:
        !plain.is(anyQualified)

        when:
        def recreation = ((DefaultBeanContext) recreating).tryRecreate(anyQualified)
        def held = recreating.getActiveBeanRegistrations(MyAnyEngine)*.bean

        then: "a new instance is held for the any qualifier, beside the singleton of the lookup without a qualifier"
        recreation == DefaultBeanContext.Recreation.RECREATED
        held.size() == 2
        held.any { it.is(plain) }
        !held.any { it.is(anyQualified) }
        def recreated = held.find { !it.is(plain) }
        recreating.getBean(MyAnyEngine, Qualifiers.any()).is(recreated)
        recreating.getBean(MyAnyEngine).is(plain)

        cleanup:
        recreating?.close()
    }

    void 'a custom scope removes an @Any bean resolved without a qualifier'() {
        given:
        def scope = context.getBean(scopeType)
        def definition = context.getBeanDefinition(beanType)

        when:
        def registration = context.getBeanRegistration(definition, Argument.of(beanType))
        def bean = registration.bean

        then:
        bean.is(context.getBean(beanType))
        scope.beans.size() == 1

        when: "removed on a thread of its own, since a removal that misses the entry retries without end"
        def removal = new FutureTask<Optional<?>>({ scope.remove(registration.beanDefinition) } as Callable<Optional<?>>)
        def thread = new Thread(removal)
        thread.daemon = true
        thread.start()
        def removed = removal.get(10, TimeUnit.SECONDS)

        then: "the bean is removed from the scope it was stored in"
        removed.get().is(bean)
        scope.beans.isEmpty()
        !context.getBean(beanType).is(bean)

        cleanup:
        scope?.beans?.clear()

        where:
        scopeType             | beanType
        AnyScopeWideScopeImpl | MyAnyScopeWideBean
        AnyPerBeanScopeImpl   | MyAnyPerBeanBean
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
