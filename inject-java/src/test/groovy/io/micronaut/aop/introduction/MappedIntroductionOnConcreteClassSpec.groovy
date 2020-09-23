/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.aop.introduction


import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.inject.AbstractTypeElementSpec

class MappedIntroductionOnConcreteClassSpec extends AbstractTypeElementSpec {

    void "test mapped introduction of new interface on concrete class"() {
        given:
            ApplicationContext applicationContext = buildContext('test.MyBeanWithMappedIntroduction', '''
package test;


import javax.inject.Singleton;

@io.micronaut.aop.introduction.ListenerAdviceMarker
@Singleton
public class MyBeanWithMappedIntroduction {
}

''')
            applicationContext.registerSingleton(new ListenerAdviceInterceptor())

        when:
            def beanClass = applicationContext.classLoader.loadClass('test.MyBeanWithMappedIntroduction')

            def cc = applicationContext.getBean(beanClass)
            def listenerAdviceInterceptor = applicationContext.getBean(ListenerAdviceInterceptor)

        then:
            cc instanceof ApplicationEventListener

        when:
            def event = new StartupEvent(applicationContext)
            cc.onApplicationEvent(event)

        then:
            listenerAdviceInterceptor.recievedMessages.contains(event)

        cleanup:
            listenerAdviceInterceptor.recievedMessages.clear()

    }
}
