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
package org.particleframework.validation

import org.particleframework.context.ApplicationContext
import org.particleframework.context.BeanContext
import spock.lang.Specification

import javax.validation.ConstraintViolationException

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ValidatedSpec extends Specification {

    def "test validated annotation validates beans"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run("test")
        Foo foo = beanContext.getBean(Foo)

        when:"invalid data is passed"

        foo.testMe("aaa")

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'testMe.number: numeric value out of bounds (<3 digits>.<2 digits> expected)'

        when:"valid data is passed"
        def result = foo.testMe("100.00")

        then:
        result == "\$100.00"

    }

}


