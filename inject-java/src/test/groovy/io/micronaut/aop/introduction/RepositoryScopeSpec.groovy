/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.introduction

import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RepositoryScopeSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()

    void "test default repository scope is prototype"() {
        when:
            def instance1 = beanContext.getBean(MyPrototypeRepo)
            def instance2 = beanContext.getBean(MyPrototypeRepo)
        then:
            !instance1.is(instance2)
    }

    void "test no memory leak 1"() {
        when:
            def introducer = getDataInterceptor()
            def instance = beanContext.getBean(MyPrototypeRepo)
        then:
            instance.deleteById(111)
            introducer.methods.size() == 1
            introducer.repoMethods.size() == 1
        where:
            i << (1..1000)
    }

    void "test no memory leak 2"() {
        when:
            def introducer = getDataInterceptor()
            def instance = myPrototypeRepo
        then:
            instance.deleteById(123)
            introducer.methods.size() == 1
            introducer.repoMethods.size() == 1
        where:
            i << (1..1000)
    }

    void "test no memory leak 3"() {
        when:
            def introducer = getDataInterceptor()
            def myService = beanContext.getBean(MyPrototypeService)
        then:
            myService.myPrototypeRepo.deleteById(123)
            introducer.methods.size() == 1
            introducer.repoMethods.size() == 1
        where:
            i << (1..1000)
    }

    @Memoized
    private MyPrototypeRepoIntroducer getDataInterceptor() {
        return beanContext.getBean(MyPrototypeRepoIntroducer)
    }

    @Memoized
    MyPrototypeRepo getMyPrototypeRepo() {
        beanContext.getBean(MyPrototypeRepo)
    }

    @Prototype
    static class MyPrototypeService {

        final MyPrototypeRepo myPrototypeRepo

        MyPrototypeService(MyPrototypeRepo myPrototypeRepo) {
            this.myPrototypeRepo = myPrototypeRepo
        }
    }
}
