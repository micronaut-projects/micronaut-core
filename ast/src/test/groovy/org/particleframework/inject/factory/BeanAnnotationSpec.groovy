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
package org.particleframework.inject.factory

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.annotation.Bean
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class BeanAnnotationSpec extends Specification{

    void "test @bean annotation makes a class available as a bean"() {

        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        expect:
        beanContext.getBean(A) != beanContext.getBean(A) // prototype by default
    }

    @Bean
    static class A {

    }
}
