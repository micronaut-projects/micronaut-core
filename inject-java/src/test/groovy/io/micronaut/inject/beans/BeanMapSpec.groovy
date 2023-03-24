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
package io.micronaut.inject.beans

import io.micronaut.core.beans.BeanMap
import io.micronaut.core.beans.exceptions.IntrospectionException
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class BeanMapSpec extends Specification {

    void "test bean map"() {
        when:
        def bean = new MyBean("test")
        bean.bool = true
        bean.URL = new URL("http://google.com")
        bean.str = "blah"
        bean.foo = "bar"
        bean.sup = "parent"
        BeanMap beanMap = BeanMap.of(bean)

        then:
        beanMap.get("bool")
        beanMap.get("URL") == new URL("http://google.com")
        beanMap.get("str") == "blah"
        beanMap.get("foo") == 'bar'
        beanMap.get("sup") == 'parent'
        beanMap.get("readOnly") == "test"
        beanMap.size() == 6
        !beanMap.containsKey('metaClass')
        !beanMap.containsKey('class')

        when:
        BeanMap.of(new NoProps())

        then:
        thrown(IntrospectionException)
    }

}

