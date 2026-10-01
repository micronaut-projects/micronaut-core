package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.function.Supplier

class ReflectionBeanDefinitionSpec extends Specification {

    void "a reflective bean is instantiated, injected and initialized like a generated one"() {
        given:
        def context = ApplicationContext.run(
                'dispatcher.region': 'north',
                'dispatcher.retries': '3',
                'dispatcher.label': 'labelled'
        )
        context.registerBeanDefinition(ReflectionBeanDefinition.of(Warehouse))
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Courier, { new Courier("express") } as Supplier<Courier>).named("express").singleton(true).build())
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Courier, { new Courier("slow") } as Supplier<Courier>).named("slow").singleton(true).build())
        def definition = ReflectionBeanDefinition.of(Dispatcher)
        context.registerBeanDefinition(definition)

        when:
        def dispatcher = context.getBean(Dispatcher)

        then: "the constructor arguments of every kind are resolved"
        dispatcher.warehouse.is(context.getBean(Warehouse))
        dispatcher.couriers*.code.sort() == ["express", "slow"]
        dispatcher.courierArray*.code.sort() == ["express", "slow"]
        dispatcher.couriersByName.keySet().sort() == ["express", "slow"]
        dispatcher.couriersByName.slow.code == "slow"
        dispatcher.missing.empty
        dispatcher.region == "north"
        dispatcher.retries == 3

        and: "the fields, the inherited ones included"
        dispatcher.expressCourier.code == "express"
        dispatcher.slowCourier.code == "slow"
        dispatcher.label == "labelled"
        dispatcher.warehouseRegistration.bean.is(dispatcher.warehouse)
        dispatcher.baseWarehouse.is(dispatcher.warehouse)

        and: "the methods, the setters annotated with a value included"
        dispatcher.courierStream.count() == 2
        dispatcher.zone == "west"
        dispatcher.setterWarehouse.is(dispatcher.warehouse)

        and: "the post construct method ran"
        dispatcher.started
        dispatcher.events == ["start"]

        and: "the bean has the scope of its class"
        context.getBean(Dispatcher).is(dispatcher)
        definition.singleton
        definition.scope.get() == Singleton
        definition.beanType == Dispatcher
        definition.beanDefinitionName.startsWith(Dispatcher.name)

        and: "the definition describes its injection points"
        definition.constructor.arguments*.name == ["warehouse", "couriers", "courierArray", "couriersByName", "missing", "region", "retries"]
        definition.constructor.arguments[5].annotationMetadata.stringValue("io.micronaut.context.annotation.Value").get() == '${dispatcher.region}'
        definition.injectedFields*.name.sort() == ["baseWarehouse", "expressCourier", "label", "slowCourier", "warehouseRegistration"]
        definition.injectedFields*.name.indexOf("baseWarehouse") == 0
        definition.injectedMethods*.name.sort() == ["injectBase", "setCourierStream", "setZone", "start", "stop"]
        definition.injectedMethods*.name.indexOf("injectBase") == 0
        definition.postConstructMethods*.name == ["start"]
        definition.preDestroyMethods*.name == ["stop"]
        definition.requiredComponents.contains(Warehouse)
        definition.requiredComponents.contains(Courier)
        definition.targetConstructor.parameterCount == 7

        and: "the executable methods are the annotated ones, invoked through the context"
        definition.executableMethods*.name == ["dispatch"]
        definition.findMethod("dispatch", String).get().invoke(dispatcher, "p1") == "central:p1"
        definition.findMethod("dispatch", String).get().targetMethod == Dispatcher.getMethod("dispatch", String)
        definition.findMethod("dispatch", String).get().hasAnnotation("io.micronaut.context.annotation.Executable")
        definition.findMethod("dispatch", String).get().hasAnnotation(Singleton)
        context.getExecutionHandle(Dispatcher, "dispatch", String).invoke("p2") == "central:p2"

        when: "the context is closed"
        context.close()

        then: "the pre destroy method ran"
        dispatcher.stopped
        dispatcher.events == ["start", "stop"]
    }

    void "the builder overrides the qualifier and the scope of the class"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.builder(Prototype).named("shared").singleton(true).build())
        context.registerBeanDefinition(ReflectionBeanDefinition.builder(Prototype).named("fresh").build())

        when:
        def shared = context.getBean(Prototype, Qualifiers.byName("shared"))
        def sharedAgain = context.getBean(Prototype, Qualifiers.byName("shared"))
        def fresh = context.getBean(Prototype, Qualifiers.byName("fresh"))
        def freshAgain = context.getBean(Prototype, Qualifiers.byName("fresh"))

        then:
        shared.is(sharedAgain)
        !fresh.is(freshAgain)
        context.getBeanDefinition(Prototype, Qualifiers.byName("shared")).declaredQualifier == Qualifiers.byName("shared")

        cleanup:
        context.close()
    }

    void "a condition declared by the class is honoured"() {
        given:
        def disabled = ApplicationContext.run('conditional.enabled': 'false')
        def enabled = ApplicationContext.run('conditional.enabled': 'true')
        disabled.registerBeanDefinition(ReflectionBeanDefinition.of(Conditional))
        enabled.registerBeanDefinition(ReflectionBeanDefinition.of(Conditional))

        expect:
        disabled.findBean(Conditional).empty
        enabled.findBean(Conditional).present

        cleanup:
        disabled.close()
        enabled.close()
    }

    void "the exposed types of the builder limit the bean"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.builder(Generic).exposedTypes(Supplier).build())

        expect:
        context.findBean(Supplier).present
        context.findBean(Generic).empty

        cleanup:
        context.close()
    }

    void "the type arguments the type gives to its super types are resolved"() {
        given:
        def definition = ReflectionBeanDefinition.of(Generic)

        expect:
        definition.getTypeArguments(GenericBase)*.type == [String]
        definition.getTypeArguments(Supplier)*.type == [Integer]
        definition.getTypeArguments(Supplier.name)*.type == [Integer]
        definition.getTypeArguments(Runnable).empty
    }

    void "the builder widens the executable methods"() {
        expect:
        ReflectionBeanDefinition.of(Dispatcher).executableMethods*.name == ["dispatch"]
        ReflectionBeanDefinition.builder(Dispatcher).executableMethods { it.name == "notExecutable" }.build()
                .executableMethods*.name.sort() == ["dispatch", "notExecutable"]
    }

    void "a type that cannot be a bean is rejected"() {
        when:
        ReflectionBeanDefinition.of(type)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(reason)

        where:
        type                | reason
        Dispatcher.Missing  | "not a class"
        AbstractReservation | "abstract"
        Outer.Inner         | "non-static inner"
    }

    void "an injection point a generic super class declares is read as the bean type sees it"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.of(Warehouse))
        def definition = ReflectionBeanDefinition.of(DefGenerics.Impl)
        context.registerBeanDefinition(definition)

        when:
        def bean = context.getBean(DefGenerics.Impl)

        then: "the variable the bean type gives a value to is injected as that value, not as its bound"
        bean.dep.is(context.getBean(Warehouse))
        bean.service.is(bean.dep)

        and: "the definition describes the injection points with the resolved type"
        definition.injectedFields.find { it.name == "dep" }.type == Warehouse
        definition.injectedMethods.find { it.name == "setService" }.arguments[0].type == Warehouse
        definition.requiredComponents.contains(Warehouse)

        cleanup:
        context.close()
    }

    void "a variable an intermediate super class passes on is resolved as well"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.of(Warehouse))
        context.registerBeanDefinition(ReflectionBeanDefinition.of(DefGenerics.TwoLevel))

        when:
        def bean = context.getBean(DefGenerics.TwoLevel)

        then: "the chain up to the declaring class is walked"
        bean.dep.is(context.getBean(Warehouse))
        bean.service.is(bean.dep)

        cleanup:
        context.close()
    }

    void "a generic collection injection point is resolved to the beans of the element type"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Courier, { new Courier("express") } as Supplier<Courier>).named("express").singleton(true).build())
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Courier, { new Courier("slow") } as Supplier<Courier>).named("slow").singleton(true).build())
        def definition = ReflectionBeanDefinition.of(DefGenerics.AllCouriers)
        context.registerBeanDefinition(definition)

        when:
        def bean = context.getBean(DefGenerics.AllCouriers)

        then:
        bean.all*.code.sort() == ["express", "slow"]
        definition.injectedMethods.find { it.name == "setAll" }.arguments[0].firstTypeVariable.get().type == Courier

        cleanup:
        context.close()
    }

    void "an overridden generic member is one injection point, not two"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.of(Warehouse))
        def definition = ReflectionBeanDefinition.of(DefOverrides.Impl)
        context.registerBeanDefinition(definition)

        when:
        def bean = context.getBean(DefOverrides.Impl)

        then: "the bridge hides the erased declaration it stands for, so each method runs once"
        bean.events == ["setService:Warehouse", "init:Warehouse"]

        and: "the definition holds the override alone"
        definition.injectedMethods*.name.sort() == ["init", "setService"]
        definition.postConstructMethods*.name == ["init"]

        cleanup:
        context.close()
    }

    void "the exposed types of the class annotation limit the bean"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.of(DefJob))

        expect: "the @Bean(typed = ...) annotation of the class is honoured: the class itself is not exposed"
        context.getBeanDefinition(DefTask).exposedTypes == [DefTask] as Set
        !context.getBeanDefinition(DefTask).isCandidateBean(Argument.of(DefJob))
        context.findBean(DefJob).empty

        and: "the bean is served as the type it exposes"
        context.findBean(DefTask).present

        cleanup:
        context.close()
    }

    void "the exposed types of the builder override the ones of the class annotation"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.builder(DefJob).exposedTypes(DefJob).build())

        expect:
        context.findBean(DefJob).present
        context.findBean(DefTask).empty

        cleanup:
        context.close()
    }

    void "the bean is instantiated by the constructor or the factory the processors select"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(ReflectionBeanDefinition.of(Warehouse))
        def publicOverNoArg = ReflectionBeanDefinition.of(DefConstructors.PublicOverNoArg)
        def factory = ReflectionBeanDefinition.of(DefConstructors.Factory)
        def annotatedRecord = ReflectionBeanDefinition.of(DefConstructors.Annotated)
        [publicOverNoArg, factory, annotatedRecord].each { context.registerBeanDefinition(it) }

        expect: "with nothing annotated, the public constructor wins over the one taking no parameter"
        publicOverNoArg.targetConstructor.parameterCount == 1
        context.getBean(DefConstructors.PublicOverNoArg).warehouse.is(context.getBean(Warehouse))

        and: "a static @Creator factory is the route of a class that keeps its constructors to itself"
        factory.targetConstructor == null
        factory.targetFactoryMethod == DefConstructors.Factory.getDeclaredMethod("of", Warehouse)
        context.getBean(DefConstructors.Factory).warehouse.is(context.getBean(Warehouse))

        and: "the annotated constructor of a record wins over its canonical one"
        annotatedRecord.targetConstructor.parameterCount == 1
        context.getBean(DefConstructors.Annotated).label() == "created"

        cleanup:
        context.close()
    }

    void "a field carrying a qualifier and no @Inject is injected"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Courier, { new Courier("express") } as Supplier<Courier>).named("express").singleton(true).build())
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Courier, { new Courier("slow") } as Supplier<Courier>).named("slow").singleton(true).build())
        def definition = ReflectionBeanDefinition.of(DefQualified)
        context.registerBeanDefinition(definition)

        expect: "the qualifier of the field selects the bean, as it does for a processed field"
        definition.injectedFields*.name == ["courier"]
        context.getBean(DefQualified).courier.code == "express"

        cleanup:
        context.close()
    }

    void "the definition of a class without an accessible constructor is rejected"() {
        when:
        ReflectionBeanDefinition.of(Locked)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("no accessible constructor")
    }

    static class Outer {
        class Inner {
        }
    }
}
