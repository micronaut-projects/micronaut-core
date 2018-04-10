/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.context.event

import io.micronaut.context.BeanContext
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class EventListenerSpec extends Specification {

    void "test receive event"() {
        given:
        BeanContext context = BeanContext.run()

        when:
        context.publishEvent(new FooEvent())
        MyListener listener = context.getBean(MyListener)
        SecondListener second = context.getBean(SecondListener)

        then:
        listener.count == 1
        second.count == 0

        when:
        context.publishEvent(new FooEvent())
        context.publishEvent(new BarEvent())

        then:
        listener.count == 2
        second.count == 1


    }

}

class FooEvent {}
class BarEvent {}

@Singleton
class MyListener implements ApplicationEventListener<FooEvent> {

    int count = 0
    @Override
    void onApplicationEvent(FooEvent event) {
        count++
        event != null
    }
}

@Singleton
class SecondListener implements ApplicationEventListener<BarEvent> {

    int count = 0
    @Override
    void onApplicationEvent(BarEvent event) {
        count++
        event != null
    }
}
