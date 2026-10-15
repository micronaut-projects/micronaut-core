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
package io.micronaut.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.annotation.Retryable
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class NamedRetryPolicyResolutionSpec extends Specification {

    private static final Map<String, Object> CONFIG = [
        'spec.name'                               : 'NamedRetryPolicyResolutionSpec',
        'micronaut.retry.policies.once.attempts'  : 1,
        'micronaut.retry.policies.once.delay'     : '1ms',
        'micronaut.retry.policies.four.attempts'  : 4,
        'micronaut.retry.policies.four.delay'     : '1ms',
    ]

    void "beans sharing an inherited method keep their own named policy, whichever is called first"() {
        given:
        ApplicationContext context = ApplicationContext.run(CONFIG)
        OnceService once = context.getBean(OnceService)
        FourService four = context.getBean(FourService)
        List<BaseService> order = onceFirst ? [once, four] : [four, once]

        when:
        order.each { service ->
            try {
                service.call()
            } catch (IllegalStateException ignored) {
            }
        }

        then:
        once.calls.get() == 2 // the first call and 1 retry
        four.calls.get() == 5 // the first call and 4 retries

        cleanup:
        context.close()

        where:
        onceFirst << [true, false]
    }

    void "an unknown policy name fails the returned completion stage"() {
        given:
        ApplicationContext context = ApplicationContext.run(CONFIG)
        AsyncService service = context.getBean(AsyncService)

        when:
        CompletionStage<String> stage = service.unknownStage()

        then:
        stage != null

        when:
        stage.toCompletableFuture().get()

        then:
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message.contains('No retry policy named [missing]')
        service.calls.get() == 0

        cleanup:
        context.close()
    }

    void "an unknown policy name fails the returned publisher"() {
        given:
        ApplicationContext context = ApplicationContext.run(CONFIG)
        AsyncService service = context.getBean(AsyncService)

        when:
        Publisher<String> publisher = service.unknownPublisher()

        then:
        publisher != null

        when:
        Flux.from(publisher).blockFirst()

        then:
        IllegalStateException e = thrown()
        e.message.contains('No retry policy named [missing]')
        service.calls.get() == 0

        cleanup:
        context.close()
    }

    void "a circuit breaker retries with the named policy of its class"() {
        given:
        ApplicationContext context = ApplicationContext.run(CONFIG)
        NamedCircuitService service = context.getBean(NamedCircuitService)

        when:
        service.guarded()

        then:
        thrown(IllegalStateException)
        service.calls.get() == 5 // the first call and the 4 retries of the policy

        cleanup:
        context.close()
    }

    void "an include or exclude that is not a class fails naming the policy and the property"() {
        when:
        ApplicationContext.run([("micronaut.retry.policies.typo.$member".toString()): value]).close()

        then:
        Exception e = thrown()
        String message = messages(e)
        message.contains('Invalid retry policy [typo] of micronaut.retry.policies.typo')
        message.contains("$member must be exception types, class not found: java.io.IOExeption")

        where:
        member     | value
        'includes' | ['java.io.IOExeption']
        'includes' | ['java.io.IOException', 'java.io.IOExeption']
        'includes' | 'java.io.IOException,java.io.IOExeption'
        'excludes' | 'java.io.IOExeption'
    }

    void "includes and excludes bind from a list and from a comma separated value"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.retry.policies.list.includes' : [IOException.name, IllegalStateException.name],
            'micronaut.retry.policies.comma.includes': "${IOException.name}, ${IllegalStateException.name}".toString(),
            'micronaut.retry.policies.comma.excludes': FileNotFoundException.name,
        ])
        RetryRegistry registry = context.getBean(RetryRegistry)

        expect:
        registry.getPolicy('list').includes() == [IOException, IllegalStateException]
        registry.getPolicy('comma').includes() == [IOException, IllegalStateException]
        registry.getPolicy('comma').excludes() == [FileNotFoundException]

        cleanup:
        context.close()
    }

    void "an invalid policy fails only its own users of the registry"() {
        given:
        NamedRetryPolicyConfiguration bad = new NamedRetryPolicyConfiguration('bad')
        bad.includes = [String.name]
        NamedRetryPolicyConfiguration good = new NamedRetryPolicyConfiguration('good')
        good.attempts = 2
        def executor = Executors.newSingleThreadScheduledExecutor()
        RetryRegistry registry = new DefaultRetryRegistry([bad, good], RetryOperationsFactory.create(executor))

        expect:
        registry.names == ['bad', 'good'] as Set
        registry.getPolicy('good').maxAttempts() == 2
        registry.retry('good') != null

        when:
        registry.getPolicy('bad')

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('Invalid retry policy [bad] of micronaut.retry.policies.bad')

        cleanup:
        executor.shutdown()
    }

    void "the policies are validated at startup only when one is configured"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        !context.containsBean(NamedRetryPolicyValidator)

        cleanup:
        context.close()
    }

    private static String messages(Throwable e) {
        List<String> messages = []
        while (e != null) {
            messages << e.message
            e = e.cause == e ? null : e.cause
        }
        return messages.join('\n')
    }

    static abstract class BaseService {
        final AtomicInteger calls = new AtomicInteger()

        void call() {
            calls.incrementAndGet()
            throw new IllegalStateException("down")
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedRetryPolicyResolutionSpec')
    @Retryable(name = 'once')
    static class OnceService extends BaseService {
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedRetryPolicyResolutionSpec')
    @Retryable(name = 'four')
    static class FourService extends BaseService {
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedRetryPolicyResolutionSpec')
    static class AsyncService {
        final AtomicInteger calls = new AtomicInteger()

        @Retryable(name = 'missing')
        CompletionStage<String> unknownStage() {
            calls.incrementAndGet()
            CompletableFuture.completedFuture('ok')
        }

        @Retryable(name = 'missing')
        Publisher<String> unknownPublisher() {
            calls.incrementAndGet()
            Flux.just('ok')
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedRetryPolicyResolutionSpec')
    @Retryable(name = 'four')
    static class NamedCircuitService {
        final AtomicInteger calls = new AtomicInteger()

        @CircuitBreaker(delay = '1ms', reset = '1m')
        void guarded() {
            calls.incrementAndGet()
            throw new IllegalStateException("down")
        }
    }
}
