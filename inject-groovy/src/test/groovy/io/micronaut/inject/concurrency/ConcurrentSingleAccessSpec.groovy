/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject.concurrency

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Created by graemerocher on 17/05/2017.
 */
class ConcurrentSingleAccessSpec extends Specification {

    void "test that concurrent access to a singleton returns the same object"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        def threads = []
        Collection beans = new ConcurrentLinkedQueue<>()
        30.times {
            threads << Thread.start {
                B b = context.getBean(B)
                beans.add( b )
            }
        }
        for(Thread t in threads) {
            t.join()
        }

        then:
        beans.unique().size() == 1
    }

    @Singleton
    static class B {

    }
}
