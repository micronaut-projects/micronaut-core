/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.context.retain.veto

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.reload.AnnotatedBeanRetentionPolicy
import io.micronaut.context.reload.BeanRetentionPolicy
import io.micronaut.context.reload.PolicyRetentionCriteria
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.concurrent.Callable

import static io.micronaut.context.reload.BeanRetentionPolicy.Decision.ABSTAIN
import static io.micronaut.context.reload.BeanRetentionPolicy.Decision.REFUSE
import static io.micronaut.context.reload.BeanRetentionPolicy.Decision.RETAIN

/**
 * A {@link BeanRetentionPolicy} refuses a bean that another policy, {@link io.micronaut.context.annotation.Retain} or
 * {@code micronaut.dev.retain} retains: the refusal wins whatever the order, and the bean is destroyed and created again.
 */
class RetentionVetoSpec extends Specification {

    def setup() {
        VetoedClient.DESTROYED.clear()
        KeptClient.DESTROYED.clear()
        NamedClient.DESTROYED.clear()
    }

    void "a refused @Retain bean is destroyed once and created again, a bean the policy does not refuse is retained"() {
        given:
        ApplicationContext first = start(List.of())
        VetoedClient vetoed = first.getBean(VetoedClient)
        KeptClient kept = first.getBean(KeptClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, AnnotatedBeanRetentionPolicy.INSTANCE, refusing(VetoedClient))

        then: "the refused bean is destroyed with the context, the other one is retained"
        !retained*.bean.any { it.is(vetoed) }
        retained*.bean.any { it.is(kept) }
        VetoedClient.DESTROYED == [vetoed]
        KeptClient.DESTROYED.isEmpty()

        when:
        ApplicationContext second = start(retained)

        then: "the next context creates a new one, and adopts the retained one"
        !second.getBean(VetoedClient).is(vetoed)
        second.getBean(KeptClient).is(kept)

        when:
        VetoedClient recreated = second.getBean(VetoedClient)
        second.close()

        then: "the refused instance was destroyed exactly once, the new one with the second context"
        VetoedClient.DESTROYED.count { it.is(vetoed) } == 1
        VetoedClient.DESTROYED.count { it.is(recreated) } == 1
        KeptClient.DESTROYED == [kept]
    }

    void "an abstaining policy changes nothing"() {
        given:
        ApplicationContext first = start(List.of())
        VetoedClient vetoed = first.getBean(VetoedClient)
        KeptClient kept = first.getBean(KeptClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, AnnotatedBeanRetentionPolicy.INSTANCE, policy(0) { ABSTAIN })

        then:
        retained*.bean.any { it.is(vetoed) }
        retained*.bean.any { it.is(kept) }
        VetoedClient.DESTROYED.isEmpty()

        cleanup:
        start(retained).close()
    }

    void "a refusal wins whatever the order of the policies"() {
        given:
        ApplicationContext first = start(List.of())
        VetoedClient vetoed = first.getBean(VetoedClient)
        KeptClient kept = first.getBean(KeptClient)
        BeanRetentionPolicy retaining = policy(retainOrder) { it.beanType in [VetoedClient, KeptClient] ? RETAIN : ABSTAIN }
        BeanRetentionPolicy veto = policy(vetoOrder) { it.beanType == VetoedClient ? REFUSE : ABSTAIN }

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, retaining, veto)

        then:
        !retained*.bean.any { it.is(vetoed) }
        retained*.bean.any { it.is(kept) }

        cleanup:
        start(retained).close()

        where:
        retainOrder | vetoOrder
        -10         | 10
        10          | -10
    }

    void "a refusal covers a bean retained by type, as micronaut.dev.retain names it"() {
        given:
        ApplicationContext first = start(List.of())
        NamedClient named = first.getBean(NamedClient)

        expect: "retained by type alone"
        stopRetainingAndClose(first, byType(NamedClient)).any { it.is(named) }

        when:
        ApplicationContext next = start(List.of())
        NamedClient other = next.getBean(NamedClient)
        Collection<BeanRegistration<?>> retained = stopRetaining(next, byType(NamedClient), refusing(NamedClient))

        then: "refused, and destroyed"
        !retained*.bean.any { it.is(other) }
        NamedClient.DESTROYED.count { it.is(other) } == 1

        cleanup:
        start(retained).close()
    }

    void "a bean that holds a refused bean is not retained, nor is the refused one"() {
        given:
        ApplicationContext first = start(List.of())
        HoldingClient holding = first.getBean(HoldingClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, AnnotatedBeanRetentionPolicy.INSTANCE, refusing(HeldHandler))

        then:
        !retained*.bean.any { it.is(holding) }
        !retained*.bean.any { it.is(holding.handler) }

        when:
        ApplicationContext second = start(retained)

        then:
        !second.getBean(HoldingClient).is(holding)

        cleanup:
        second?.close()
    }

    void "the context's own refusal still wins when the policies retain a bean and none refuses it"() {
        given:
        ApplicationContext first = start(List.of())
        ContextBoundClient bound = first.getBean(ContextBoundClient)
        KeptClient kept = first.getBean(KeptClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, AnnotatedBeanRetentionPolicy.INSTANCE, refusing(VetoedClient))

        then:
        !retained*.bean.any { it.is(bound) }
        retained*.bean.any { it.is(kept) }

        cleanup:
        start(retained).close()
    }

    void "an instance registered under several types is refused when any of its registrations is"() {
        given: "one instance, registered as a Runnable and as a Callable"
        ApplicationContext first = start(List.of())
        Dual dual = new Dual()
        first.registerSingleton(Runnable, dual)
        first.registerSingleton(Callable, dual)
        BeanRetentionPolicy retainsCallable = policy(0) { it.beanType == Callable ? RETAIN : ABSTAIN }
        BeanRetentionPolicy refusesRunnable = policy(0) { it.beanType == Runnable ? REFUSE : ABSTAIN }

        expect: "retained under the accepted registration alone"
        stopRetainingAndClose(start(List.of()).tap { registerSingleton(Runnable, dual); registerSingleton(Callable, dual) }, retainsCallable)
            .any { it.is(dual) }

        when: "a policy refuses its other registration"
        Collection<BeanRegistration<?>> retained = stopRetaining(first, retainsCallable, refusesRunnable)

        then: "it is not retained under either"
        !retained*.bean.any { it.is(dual) }

        cleanup:
        start(retained).close()
    }

    static class Dual implements Runnable, Callable<Object> {
        @Override
        void run() {
        }

        @Override
        Object call() {
            null
        }
    }

    void "the debug log names the refusing policy"() {
        given:
        // logback is on the test runtime classpath only
        def logger = LoggerFactory.getLogger("io.micronaut.context.lifecycle")
        def level = logger.level
        def appender = Class.forName('ch.qos.logback.core.read.ListAppender').getDeclaredConstructor().newInstance()
        appender.start()
        logger.addAppender(appender)
        logger.level = Class.forName('ch.qos.logback.classic.Level').DEBUG
        ApplicationContext first = start(List.of())
        first.getBean(VetoedClient)
        BeanRetentionPolicy veto = new VetoingPolicy()

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, AnnotatedBeanRetentionPolicy.INSTANCE, veto)

        then:
        appender.list.any {
            it.level.toString() == 'DEBUG' && it.formattedMessage.contains('is not retained across the restart') &&
                it.formattedMessage.contains(VetoingPolicy.name) && it.formattedMessage.contains(VetoedClient.simpleName)
        }

        cleanup:
        logger.detachAppender(appender)
        logger.level = level
        start(retained).close()
    }

    private static Collection<Object> stopRetainingAndClose(ApplicationContext context, BeanRetentionPolicy... policies) {
        Collection<BeanRegistration<?>> retained = stopRetaining(context, policies)
        start(retained).close()
        return retained*.bean
    }

    private static Collection<BeanRegistration<?>> stopRetaining(ApplicationContext context, BeanRetentionPolicy... policies) {
        ((DefaultBeanContext) context).stopRetaining(new PolicyRetentionCriteria(List.of(policies), { false }, { false }))
    }

    private static BeanRetentionPolicy refusing(Class<?> type) {
        policy(0) { it.beanType == type ? REFUSE : ABSTAIN }
    }

    private static BeanRetentionPolicy byType(Class<?> type) {
        policy(0) { type.isAssignableFrom(it.beanType) ? RETAIN : ABSTAIN }
    }

    private static BeanRetentionPolicy policy(int order, Closure<BeanRetentionPolicy.Decision> decide) {
        new BeanRetentionPolicy() {
            @Override
            BeanRetentionPolicy.Decision decide(BeanRegistration<?> registration) {
                decide.call(registration)
            }

            @Override
            int getOrder() {
                order
            }
        }
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        ApplicationContext.builder()
            .properties('spec.name': 'RetentionVetoSpec')
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }

    static class VetoingPolicy implements BeanRetentionPolicy {
        @Override
        BeanRetentionPolicy.Decision decide(BeanRegistration<?> registration) {
            registration.beanType == VetoedClient ? REFUSE : ABSTAIN
        }
    }
}
