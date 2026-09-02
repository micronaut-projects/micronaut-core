package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
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
