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
import io.micronaut.retry.annotation.Retryable
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class NamedRetryPolicySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
        'spec.name'                                  : 'NamedRetryPolicySpec',
        'micronaut.retry.policies.io.attempts'       : 5,
        'micronaut.retry.policies.io.delay'          : '1ms',
        'micronaut.retry.policies.io.max-delay'      : '1s',
        'micronaut.retry.policies.io.multiplier'     : 2,
        'micronaut.retry.policies.io.jitter'         : 0.1,
        'micronaut.retry.policies.io.includes'       : [IOException.name],
        'micronaut.retry.policies.io.excludes'       : [FileNotFoundException.name],
        'micronaut.retry.policies.quick.attempts'    : 4,
        'micronaut.retry.policies.quick.delay'       : '1ms',
    ])

    void "the registry holds the configured policies"() {
        given:
        RetryRegistry registry = context.getBean(RetryRegistry)
        RetryPolicy policy = registry.getPolicy('io')

        expect:
        registry.names == ['io', 'quick'] as Set
        policy.maxAttempts() == 5
        policy.delay() == Duration.ofMillis(1)
        policy.maxDelay() == Duration.ofSeconds(1)
        policy.multiplier() == 2d
        policy.jitter() == 0.1d
        policy.includes() == [IOException]
        policy.excludes() == [FileNotFoundException]
        registry.findPolicy('unknown').isEmpty()
        registry.getPolicy('quick').maxAttempts() == 4
    }

    void "the registry retries with a named policy"() {
        given:
        RetryOperations operations = context.getBean(RetryRegistry).retry('quick')
        AtomicInteger calls = new AtomicInteger()

        when:
        operations.execute { calls.incrementAndGet(); throw new IllegalStateException("down") }

        then:
        thrown(IllegalStateException)
        calls.get() == 5 // the first call and 4 retries
        context.getBean(RetryRegistry).retry('quick').is(operations)
    }

    void "the registry fails clearly for an unknown name"() {
        when:
        context.getBean(RetryRegistry).retry('unknown')

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('No retry policy named [unknown]')
        e.message.contains('micronaut.retry.policies.unknown')
    }

    void "a method takes the settings of its named policy"() {
        given:
        NamedRetryService service = context.getBean(NamedRetryService)

        when: "an included exception is retried the attempts of the policy"
        service.named(new IOException("io"))

        then:
        thrown(IOException)
        service.calls.getAndSet(0) == 6 // the first call and 5 retries

        when: "an exception that the policy does not include is not retried"
        service.named(new IllegalStateException("state"))

        then:
        thrown(IllegalStateException)
        service.calls.getAndSet(0) == 1

        when: "an exception that the policy excludes is not retried"
        service.named(new FileNotFoundException("missing"))

        then:
        thrown(FileNotFoundException)
        service.calls.getAndSet(0) == 1
    }

    void "the members set on the annotation override the named policy"() {
        given:
        NamedRetryService service = context.getBean(NamedRetryService)

        when: "the attempts of the annotation win, the includes stay those of the policy"
        service.overriddenAttempts(new IOException("io"))

        then:
        thrown(IOException)
        service.calls.getAndSet(0) == 3

        when:
        service.overriddenAttempts(new IllegalStateException("state"))

        then:
        thrown(IllegalStateException)
        service.calls.getAndSet(0) == 1

        when: "the includes of the annotation replace those of the policy"
        service.overriddenIncludes(new IllegalStateException("state"))

        then:
        thrown(IllegalStateException)
        service.calls.getAndSet(0) == 6

        when:
        service.overriddenIncludes(new IOException("io"))

        then:
        thrown(IOException)
        service.calls.getAndSet(0) == 1
    }

    void "a method with an unknown policy name fails on its first call"() {
        given:
        NamedRetryService service = context.getBean(NamedRetryService)

        when:
        service.unknown()

        then:
        IllegalStateException e = thrown()
        e.message.contains('No retry policy named [missing]')
        e.message.contains('micronaut.retry.policies.missing')
        e.message.contains('@Retryable(name = "missing")')
        service.calls.getAndSet(0) == 0
    }

    void "a policy with an include that is not an exception fails at startup"() {
        when:
        ApplicationContext.run([
            'micronaut.retry.policies.bad.includes': [String.name],
        ]).withCloseable { it.getBean(RetryRegistry) }

        then:
        Exception e = thrown()
        rootCause(e).message.contains('includes must be exception types, got java.lang.String')
    }

    private static Throwable rootCause(Throwable e) {
        while (e.cause != null && e.cause != e) {
            e = e.cause
        }
        return e
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedRetryPolicySpec')
    static class NamedRetryService {

        final AtomicInteger calls = new AtomicInteger()

        @Retryable(name = 'io')
        void named(Exception e) {
            calls.incrementAndGet()
            throw e
        }

        @Retryable(name = 'io', attempts = '2')
        void overriddenAttempts(Exception e) {
            calls.incrementAndGet()
            throw e
        }

        @Retryable(name = 'io', includes = IllegalStateException)
        void overriddenIncludes(Exception e) {
            calls.incrementAndGet()
            throw e
        }

        @Retryable(name = 'missing')
        void unknown() {
            calls.incrementAndGet()
        }
    }
}
