package io.micronaut.inject.provider.lookuppoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.inject.FieldInjectionPoint
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LookupInjectionPointSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder()
            .properties("spec": getClass().getSimpleName())
            .beanDependencyTrackingEnabled(true)
            .beanDefinitionsProvider { ClassLoader classLoader ->
                new DefaultBeanDefinitionsProvider().provide(classLoader) + [new LookupDefinition()]
            }
            .start()

    void "a bean a provider creates later sees the point the provider was injected at"() {
        given:
        Holder holder = context.getBean(Holder)

        when:
        RedService service = holder.services.get()

        then:
        service.point instanceof FieldInjectionPoint
        service.point.declaringBean.beanType == Holder
        (service.point as FieldInjectionPoint).name == 'services'
    }

    void "a bean the dependency resolver creates later sees the segment the lookup passes"() {
        given:
        Holder holder = context.getBean(Holder)

        when:
        RedService service = holder.lookup.get().bean

        then:
        service.point instanceof RenamedSegment
        service.point.declaringBean.beanType == Holder
        service.point.toString() == 'Holder.later'

        and: 'a bean the resolution context resolved at a segment of its own saw it'
        holder.lookup.eager.bean.point.toString() == 'Holder.eager'
    }

    void "a bean created later at a lookup point is owned by the bean the lookup was injected into"() {
        given:
        BeanRegistration<Holder> holder = context.getBeanRegistration(Holder, null)
        BeanRegistration<RedService> looked = holder.bean.lookup.get()
        RedService eager = holder.bean.lookup.eager.bean
        RedService provided = holder.bean.services.get()

        expect:
        !looked.bean.destroyed
        !eager.destroyed

        and: 'the owner receives the bean once at the lookup point, and holds what its provider resolves lazily'
        def edges = context.findDependencyGraph().get().dependenciesOf(holder).findAll { it.dependency().beanType == RedService }
        edges.findAll { !it.lazy() }.size() == 1
        edges.findAll { it.lazy() }.size() == 1

        when:
        holder.bean.lookup.resolver().destroy(looked)

        then:
        looked.bean.destroyed
        !eager.destroyed

        when:
        holder.close()

        then:
        eager.destroyed

        and: 'a provider owns nothing: what it resolves outlives the bean it was injected into'
        !provided.destroyed
    }

    void "concurrent lookups at the same point do not share a path"() {
        given:
        Holder holder = context.getBean(Holder)
        int threads = 8
        def executor = Executors.newFixedThreadPool(threads)
        def ready = new CountDownLatch(threads)
        def go = new CountDownLatch(1)

        when:
        def futures = (1..threads).collect {
            executor.submit {
                ready.countDown()
                go.await(10, TimeUnit.SECONDS)
                (1..200).collect { holder.lookup.get().bean.point.toString() } +
                        (1..200).collect { holder.services.get().point.toString() }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        def points = futures.collect { it.get(30, TimeUnit.SECONDS) }

        then:
        points.every { it.take(200).every { p -> p == 'Holder.later' } }
        points.every { it.drop(200).every { p -> p.contains('services') } }

        cleanup:
        executor.shutdownNow()
    }
}
