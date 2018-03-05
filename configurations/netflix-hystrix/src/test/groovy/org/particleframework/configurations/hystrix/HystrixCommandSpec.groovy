/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.configurations.hystrix

import com.netflix.hystrix.HystrixInvokable
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook
import io.reactivex.Flowable
import io.reactivex.Single
import org.particleframework.configurations.hystrix.annotation.Hystrix
import org.particleframework.configurations.hystrix.annotation.HystrixCommand
import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Property
import org.particleframework.retry.annotation.Retryable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class HystrixCommandSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run()

    void setup() {
        applicationContext.getBean(MyHook).reset()
    }

    void "test run simple hystrix command"() {
        given:
        TestService testService = applicationContext.getBean(TestService)
        CountService countService = applicationContext.getBean(CountService)

        MyHook myHook = applicationContext.getBean(MyHook)

        when:
        String result = testService.hello("Fred")

        then:
        result == "Hello Fred"
        myHook.executed.size() == 1
        myHook.executed.first().properties.circuitBreakerEnabled().get()
        myHook.executed[0].commandKey.toString() == 'hello'
        myHook.executed[0].commandGroup.toString() == TestService.simpleName

        when:
        countService.getCount()

        then:
        countService.countValue == 3
        myHook.executed.size() == 4

        when:

        myHook.executed.clear()
        myHook.emitted.clear()
        Flowable<String> f = testService.helloFlowable("Fred")


        then:
        myHook.executed.size() ==0

        when:
        String v = f.blockingFirst()

        then:
        v == "Hello Fred"
        myHook.executed.size() == 1
        myHook.emitted.size() ==1
        myHook.executed[0].commandKey.toString() == 'helloFlowable'
        myHook.executed[0].commandGroup.toString() == TestService.simpleName

        myHook.emitted.first() == "Hello Fred"
    }

    void "test customize hystrix command keys"() {
        given:
        TestService testService = applicationContext.getBean(TestService)
        CountService countService = applicationContext.getBean(CountService)

        MyHook myHook = applicationContext.getBean(MyHook)
        myHook.reset()

        when:
        String result = testService.another("Fred", 10)

        then:
        result == 'Hello Fred aged 10'
        myHook.executed.size() == 1
        myHook.emitted.size() == 1
        !myHook.executed.first().properties.circuitBreakerEnabled().get()
        myHook.executed.first().commandKey.toString() == 'foo-bar'
        myHook.executed.first().commandGroup.toString() == 'some-group'
        myHook.executed.first().threadPoolKey.toString() == 'some-pool'

        when:
        myHook.reset()
        Single<String> single = testService.anotherFlow("Bob", 20)

        then:
        myHook.emitted.size() == 0

        when:
        result = single.blockingGet()

        then:
        result == 'Hello Bob aged 20'
        myHook.executed.size() == 1
        myHook.emitted.size() == 1
        !myHook.executed.first().properties.circuitBreakerEnabled().get()
        myHook.executed.first().commandKey.toString() == 'foo-bar-rx'
        myHook.executed.first().commandGroup.toString() == 'some-group'

        when:
        myHook.reset()
        countService.countValue = 0
        countService.countThreshold = Integer.MAX_VALUE // can't be reached
        countService.getCount()

        then:
        thrown(IllegalStateException)
        myHook.errors.size() == 6
        myHook.executed.first().commandKey.toString() == 'getCount'
        myHook.executed.first().commandGroup.toString() == 'count'
        myHook.executed.first().threadPoolKey.toString() == 'counter-pool'

    }

    @Singleton
    static class MyHook extends HystrixCommandExecutionHook {
        List<HystrixInvokable> executed = []
        List emitted = []
        List<Exception> errors = []
        @Override
        def <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
            executed.add(commandInstance)
            emitted.add(value)
            return super.onEmit(commandInstance, value)
        }

        @Override
        def <T> Exception onError(HystrixInvokable<T> commandInstance, HystrixRuntimeException.FailureType failureType, Exception e) {
            errors.add(e)
            executed.add(commandInstance)
            return super.onError(commandInstance, failureType, e)
        }

        void reset() {
            errors.clear()
            executed.clear()
            emitted.clear()
        }
    }

    @Singleton
    static class TestService {


        @HystrixCommand
        String hello(String name) {
            return "Hello $name"
        }

        @HystrixCommand(
                command = "foo-bar",
                group = "some-group",
                threadPool = 'some-pool',
                properties = @Property(
                        name = "circuitBreakerEnabled",
                        value = "false"
                )
        )
        String another(String name, int age) {
            return "Hello $name aged $age"
        }

        @HystrixCommand(
                command = "foo-bar-rx",
                group = "some-group",
                threadPool = 'some-pool',
                properties = @Property(
                        name = "circuitBreakerEnabled",
                        value = "false"
                )

        )
        Single<String> anotherFlow(String name, int age) {
            return Single.just("Hello $name aged $age".toString())
        }

        @HystrixCommand
        Flowable<String> helloFlowable(String name) {
            return Flowable.just("Hello $name".toString())
        }
    }

    @Singleton
    @Hystrix(group = "count", threadPool = 'counter-pool')
    static class CountService {

        int countValue = 0
        int countRx = 0
        int countReact = 0
        int countThreshold = 3


        @HystrixCommand
        @Retryable(attempts = '5', delay = '10ms')
        int getCount() {
            countValue++
            if(countValue < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return countValue
        }

    }
}
