/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class SealedModifierSpec extends AbstractTypeElementSpec {

    void "test around advice on a sealed class doesn't compile"() {
        when:
        buildContext('test.MyBean', '''
package test;

import io.micronaut.aop.simple.*;

@Mutating("someVal")
@jakarta.inject.Singleton
sealed class MyBean permits MyBean.Only {

    public String someMethod() {
        return "x";
    }

    static non-sealed class Only extends MyBean {
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyBean'
    }

    void "test introduction advice on a sealed interface doesn't compile"() {
        when:
        buildContext('test.MyContract', '''
package test;

import io.micronaut.aop.introduction.*;

@Stub
@jakarta.inject.Singleton
sealed interface MyContract permits MyContract.Only {

    String someMethod();

    final class Only implements MyContract {
        public String someMethod() {
            return "only";
        }
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyContract'
    }

    void "test scoped proxy on a sealed class doesn't compile"() {
        when:
        buildContext('test.MyBean', '''
package test;

import io.micronaut.inject.scope.custom.definitionlookup.LookupProxyScope;

@LookupProxyScope
sealed class MyBean permits MyBean.Only {

    public String someMethod() {
        return "x";
    }

    static non-sealed class Only extends MyBean {
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyBean'
    }

    void "test introduction advice on a sealed abstract class doesn't compile"() {
        when:
        buildContext('test.MyContract', '''
package test;

import io.micronaut.aop.introduction.*;

@Stub
@jakarta.inject.Singleton
sealed abstract class MyContract permits MyContract.Only {

    public abstract String someMethod();

    static non-sealed class Only extends MyContract {
        public String someMethod() {
            return "only";
        }
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyContract'
    }

    void "test around advice on a sealed type produced by a factory doesn't compile"() {
        when:
        buildContext('test.MyBeanFactory', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;

@Factory
class MyBeanFactory {
    @Mutating("someVal")
    @jakarta.inject.Singleton
    MyBean myBean() {
        return new MyBean.Only();
    }
}

sealed class MyBean permits MyBean.Only {

    public String someMethod() {
        return "x";
    }

    static non-sealed class Only extends MyBean {
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyBean'
    }

    void "test around advice on a non-sealed subclass of a sealed class still works"() {
        when:
        def context = buildContext('test.MyBean', '''
package test;

import io.micronaut.aop.simple.*;

sealed class MyBase permits MyBean {

    public String someMethod() {
        return "x";
    }
}

@Mutating("someVal")
@jakarta.inject.Singleton
non-sealed class MyBean extends MyBase {
}
''')
        def bean = getBean(context, 'test.MyBean')

        then:
        bean instanceof io.micronaut.aop.Intercepted

        cleanup:
        context.close()
    }
}
