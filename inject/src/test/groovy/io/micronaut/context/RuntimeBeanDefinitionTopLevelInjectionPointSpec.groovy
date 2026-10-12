package io.micronaut.context

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.Argument
import io.micronaut.inject.InjectionPoint
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import spock.lang.Specification

/**
 * A runtime bean that declares an {@link InjectionPoint} dependency and is obtained by a top level lookup was not
 * injected anywhere: a nullable one resolves to null, as for a compiled bean.
 */
class RuntimeBeanDefinitionTopLevelInjectionPointSpec extends Specification {

    private static MutableAnnotationMetadata nullableMetadata() {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, [:])
        metadata
    }

    private static RuntimeBeanDefinition<Thing> thing(Argument<?> injectionPoint) {
        RuntimeBeanDefinition.builder(Thing, ctx -> new Thing(ctx.getInjectedBean(0) as InjectionPoint<?>))
                .injectionPoint(injectionPoint)
                .build()
    }

    void 'test a nullable declared injection point is null for a top level lookup'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(thing(Argument.of(InjectionPoint, "ip", nullableMetadata())))
                .build()
                .start()

        expect:
        context.getBean(Thing).ip == null

        cleanup:
        context.close()
    }

    void 'test a nullable declared injection point describes the injection when the bean is injected'() {
        given:
        def context = ApplicationContext.builder()
                .properties('spec.name': 'RuntimeBeanDefinitionTopLevelInjectionPointSpec')
                .beanDefinitions(thing(Argument.of(InjectionPoint, "ip", nullableMetadata())))
                .build()
                .start()

        when:
        def ip = context.getBean(Consumer).thing.ip

        then:
        ip != null
        ip.declaringBean.beanType == Consumer

        cleanup:
        context.close()
    }

    static class Thing {
        final InjectionPoint<?> ip

        Thing(InjectionPoint<?> ip) {
            this.ip = ip
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RuntimeBeanDefinitionTopLevelInjectionPointSpec")
    static class Consumer {
        final Thing thing

        Consumer(Thing thing) {
            this.thing = thing
        }
    }
}
