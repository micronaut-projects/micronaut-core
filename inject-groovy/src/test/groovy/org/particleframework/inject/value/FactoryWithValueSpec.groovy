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
package org.particleframework.inject.value

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.annotation.Bean
import org.particleframework.context.annotation.Factory
import org.particleframework.context.annotation.Value
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FactoryWithValueSpec extends Specification {

    void "test configuration injection with @Value"() {
        given:
        ApplicationContext context = ApplicationContext.run('foo.bar':'8080')
        A a = context.getBean(A)
        B b = context.getBean(B)

        expect:
        a.port == 8080
        b.a != null
        b.port == 8080
    }

    static class A {
        int port
        A(int port) {
            this.port = port
        }
    }

    static class B {
        A a
        int port

        B(A a, int port) {
            this.a = a
            this.port = port
        }
    }

    @Factory
    static class MyFactory {
        @Bean
        A newA(@Value('${foo.bar}') int port) {
            return new A(port)
        }

        @Bean
        B newB(A a, @Value('${foo.bar}') int port) {
            return new B(a, port)
        }
    }
}
