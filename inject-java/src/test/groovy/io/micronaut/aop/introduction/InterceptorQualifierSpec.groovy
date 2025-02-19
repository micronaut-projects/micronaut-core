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
package io.micronaut.aop.introduction

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InterceptorQualifierSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(["spec.name": "InterceptorQualifierSpec"])

    void "test intercepted qualifier"() {
        when:
            def fooHelper = applicationContext.getBean(MyDataSourceHelper, Qualifiers.byName("FOO"))
        then:
            fooHelper.name == "FOO"
            fooHelper.injectionPointQualifier == "FOO"
            fooHelper.helper2.name == null
            fooHelper.helper2.injectionPointQualifier == "FOO"
            fooHelper.helper3.name == "FOO"
            fooHelper.helper3.injectionPointQualifier == "FOO"
        when:
            def barHelper = applicationContext.getBean(MyDataSourceHelper, Qualifiers.byName("BAR"))
        then:
            barHelper.name == "BAR"
            barHelper.helper2.name == null
        when:
            def fooInterceptor = applicationContext.getBean(MyInterceptedInterface, Qualifiers.byName("FOO"))
        then:
            fooInterceptor.value == "FOO"
        when:
            def barInterceptor = applicationContext.getBean(MyInterceptedInterface, Qualifiers.byName("BAR"))
        then:
            barInterceptor.value == "BAR"
        when:
            def fooInterceptorWrapper = applicationContext.getBean(MyInterceptedInterfaceWrapper, Qualifiers.byName("FOO"))
        then:
            fooInterceptorWrapper.myInterceptedInterface.value == "FOO"
        when:
            def barInterceptorWrapper = applicationContext.getBean(MyInterceptedInterfaceWrapper, Qualifiers.byName("BAR"))
        then:
            barInterceptorWrapper.myInterceptedInterface.value == "BAR"
    }

}
