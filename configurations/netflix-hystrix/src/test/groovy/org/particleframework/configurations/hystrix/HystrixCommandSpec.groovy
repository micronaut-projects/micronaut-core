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
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook
import io.reactivex.Flowable
import org.particleframework.configurations.hystrix.annotation.HystrixCommand
import org.particleframework.context.ApplicationContext
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
        myHook.executed[0].commandKey.toString() == 'hello'
        myHook.executed[0].commandGroup.toString() == TestService.simpleName

        when:
        countService.getCount()

        then:
        countService.countValue == 1 // hystrix doesn't retry
        def e = thrown(IllegalStateException)
        e.message == "Bad count"


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

    @Singleton
    static class MyHook extends HystrixCommandExecutionHook {
        List<HystrixInvokable> executed = []
        List emitted = []

        @Override
        def <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
            executed.add(commandInstance)
            emitted.add(value)
            return super.onEmit(commandInstance, value)
        }
    }

    @Singleton
    static class TestService {


        @HystrixCommand
        String hello(String name) {
            return "Hello $name"
        }

        @HystrixCommand
        Flowable<String> helloFlowable(String name) {
            return Flowable.just("Hello $name".toString())
        }
    }

    @Singleton
    static class CountService {

        int countValue = 0
        int countRx = 0
        int countReact = 0
        int countThreshold = 3


        @HystrixCommand
        int getCount() {
            countValue++
            if(countValue < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return countValue
        }

    }
}
