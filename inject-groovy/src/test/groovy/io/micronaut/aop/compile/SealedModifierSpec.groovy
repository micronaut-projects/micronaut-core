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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.writer.BeanDefinitionWriter

class SealedModifierSpec extends AbstractBeanDefinitionSpec {

    void "test around advice on a sealed class doesn't compile"() {
        when:
        buildBeanDefinition('test.$SealedMyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.aop.proxytarget.*

@Mutating("someVal")
@jakarta.inject.Singleton
sealed class SealedMyBean permits SealedMyBeanOnly {

    String someMethod() {
        return "x"
    }
}

non-sealed class SealedMyBeanOnly extends SealedMyBean {
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.SealedMyBean'
    }

    void "test introduction advice on a sealed interface doesn't compile"() {
        when:
        buildBeanDefinition('test.$SealedMyContract' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.aop.introduction.*

@Stub
@jakarta.inject.Singleton
sealed interface SealedMyContract permits SealedMyContractOnly {

    String someMethod()
}

non-sealed class SealedMyContractOnly implements SealedMyContract {
    String someMethod() {
        return "only"
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.SealedMyContract'
    }

    void "test around advice on a non-sealed subclass of a sealed class still compiles"() {
        when:
        def definition = buildBeanDefinition('test.$SealedMyBean2' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.aop.proxytarget.*

sealed class SealedMyBase permits SealedMyBean2 {

    String someMethod() {
        return "x"
    }
}

@Mutating("someVal")
@jakarta.inject.Singleton
non-sealed class SealedMyBean2 extends SealedMyBase {
}
''')
        then:
        definition != null
    }
}
