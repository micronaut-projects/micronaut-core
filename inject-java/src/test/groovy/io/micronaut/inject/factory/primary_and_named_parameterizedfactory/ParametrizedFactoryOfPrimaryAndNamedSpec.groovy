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
package io.micronaut.inject.factory.primary_and_named_parameterizedfactory

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class ParametrizedFactoryOfPrimaryAndNamedSpec extends Specification {

    void "test parametrized factory definition that defines a primary association"() {
        given:
            ApplicationContext beanContext = ApplicationContext.run(['confbeans.xyz': [:],
                                                                     'confbeans.abc': [:]])
            def primaryBean = beanContext.getBean(MyBeanUser)
            def xyzBean = beanContext.getBean(MyBeanUser, Qualifiers.byName("xyz"))
            def abcBean = beanContext.getBean(MyBeanUser, Qualifiers.byName("abc"))

        expect:
            primaryBean.name == "Primary"
            primaryBean.myBean.name == "myPrimary"
            primaryBean.myBean.myAssocBean.name == "myPrimary"
            xyzBean.name == "xyz"
            xyzBean.myBean.name == "xyz"
            xyzBean.myBean.myAssocBean.name == "xyz"
            abcBean.name == "abc"
            abcBean.myBean.name == "abc"
            abcBean.myBean.myAssocBean.name == "abc"

        cleanup:
            beanContext.close()
    }
}
