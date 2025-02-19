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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MyAbstractRepoSpec extends AbstractTypeElementSpec {

    void "test abstract interceptor method"() {
        given:
            def context = buildContext("""
package test;

import io.micronaut.aop.introduction.Tx;
import io.micronaut.aop.introduction.RepoDef;
import io.micronaut.aop.introduction.DeleteByIdCrudRepo;
import io.micronaut.context.annotation.Executable;

@Tx
@RepoDef
abstract class MyAbstractRepo4 implements DeleteByIdCrudRepo<Integer> {

    public String findById(Integer id) {
        return "ABC";
    }

}

""")

        when:
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("test.MyAbstractRepo4"))
            def findById = beanDef1.getRequiredMethod("findById", Integer)
        then:
            findById

        cleanup:
            context.close()
    }

    void "test default interceptor method"() {
        given:
            def context = buildContext("""
package test;

import io.micronaut.aop.introduction.Tx;
import io.micronaut.aop.introduction.RepoDef;
import io.micronaut.aop.introduction.DeleteByIdCrudRepo;

@Tx
@RepoDef
interface MyDefaultRepo extends DeleteByIdCrudRepo<Integer> {

    default String findById(Integer id) {
        return "ABC";
    }

}

""")

        when:
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("test.MyDefaultRepo"))
            def findById = beanDef1.getRequiredMethod("findById", Integer)
        then:
            findById

        cleanup:
            context.close()
    }
}
