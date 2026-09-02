package io.micronaut.inject.scope.custom.perbean

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanCreationException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PerBeanLockingScopeSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "under the scope-wide lock a creation cannot wait for another thread creating a bean of the scope"() {
        when:
        def waiter = context.getBean(ScopeWideWaiter)

        then: "the other thread only got to create its bean once the waiter's creation released the lock"
        !waiter.otherCreatedInTime
        context.getBean(ScopeWideOther)
    }

    void "under a lock per bean a creation can wait for another thread creating a bean of the scope"() {
        when:
        def waiter = context.getBean(PerBeanWaiter)

        then:
        waiter.otherCreatedInTime
        context.getBean(PerBeanOther)
    }

    void "under a lock per bean concurrent resolutions of one bean create it once"() {
        given:
        int threads = 8
        def executor = Executors.newFixedThreadPool(threads)
        def start = new CountDownLatch(1)

        when:
        def futures = (1..threads).collect {
            executor.submit({
                start.await()
                context.getBean(PerBeanRaced)
            } as java.util.concurrent.Callable<PerBeanRaced>)
        }
        start.countDown()
        def beans = futures.collect { it.get(10, TimeUnit.SECONDS) }

        then:
        beans.toSet().size() == 1
        PerBeanRaced.CONSTRUCTIONS.get() == 1

        cleanup:
        executor.shutdownNow()
    }

    void "under a lock per bean a creation creates another bean of the scope on the same thread"() {
        when:
        def dependent = context.getBean(PerBeanDependent)

        then:
        dependent.other.is(context.getBean(PerBeanOther))
    }

    void "under a lock per bean a failed creation is not held and is attempted again"() {
        when:
        context.getBean(PerBeanFaulty)

        then:
        thrown(BeanCreationException)
        PerBeanFaulty.ATTEMPTS.get() == 1

        when:
        context.getBean(PerBeanFaulty)

        then:
        thrown(BeanCreationException)
        PerBeanFaulty.ATTEMPTS.get() == 2
        !context.getBean(PerBeanScopeImpl).beans.values().any { it.definition().beanType == PerBeanFaulty }
    }

    void "under a lock per bean remove destroys the bean and the next resolution creates a new one"() {
        given:
        def scope = context.getBean(PerBeanScopeImpl)
        def first = context.getBean(PerBeanOther)
        def id = scope.beans.find { it.value.bean().is(first) }.key

        when:
        def removed = scope.remove(id)

        then:
        removed.present
        removed.get().is(first)
        first.destroyed
        !scope.beans.containsKey(id)

        when:
        def second = context.getBean(PerBeanOther)

        then:
        !second.is(first)
        !second.destroyed
    }

    void "under a lock per bean the scope answers the registration of a bean it holds"() {
        given:
        def bean = context.getBean(PerBeanOther)

        expect:
        context.getBean(PerBeanScopeImpl).findBeanRegistration(bean).get().bean.is(bean)
        context.findBeanRegistration(bean).present
    }
}
