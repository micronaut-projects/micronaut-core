/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime.context.scope

import org.particleframework.context.ApplicationContext
import spock.lang.Ignore
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Ignore
class ThreadLocalScopeSpec extends Specification {

    void "test thread local scope"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("test")
        B b = applicationContext.getBean(B)

        when:
        b.a.num = 2

        boolean isolated = false
        Thread.start {
            isolated = b.a.num == 0
        }.join()


        then:
        isolated

    }
}

@ThreadLocal
class A {
    int num

    int total() {
        return num
    }
}

@Singleton
class B {
    private A a

    B(A a) {
        this.a = a
    }

    A getA() {
        return a
    }
}