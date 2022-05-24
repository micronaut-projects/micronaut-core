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
package io.micronaut.inject.factory.primary_parameterizedfactory

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ParametrizedFactoryOfPrimarySpec extends Specification {

    void "test parametrized factory definition that defines a primary association"() {
        given:
            ApplicationContext beanContext = ApplicationContext.run(["myBean": [["name": "xyz"]]])
//            def primaryBean = beanContext.getBean(MyBeanUser)
            def delegated = beanContext.getBean(MyBeanUser2)

        expect:
//            primaryBean.name == "xx"
//            primaryBean.myBean.name == "myPrimary"
//            primaryBean.myBean.myAssocBean.name == "ROOT"
            delegated.myBean

        cleanup:
            beanContext.close()
    }
}
