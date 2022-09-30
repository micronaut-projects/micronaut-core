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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class MyRepoIntroductionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "test generated introduction methods"() {
        when:
            def bean = applicationContext.getBean(MyRepo)
            def interceptorDeclaredMethods = Arrays.stream(bean.getClass().getMethods()).filter(m -> m.getDeclaringClass() == bean.getClass()).collect(Collectors.toList())
            def repoDeclaredMethods = Arrays.stream(MyRepo.class.getMethods()).filter(m -> m.getDeclaringClass() == MyRepo.class).collect(Collectors.toList())
        then:
            repoDeclaredMethods.size() == 3 // Groovy will exclude overridden methods (Java would have 4)
            interceptorDeclaredMethods.size() == 4 // We need to intercept overridden methods
            bean.getClass().getName().contains("Intercepted")
            MyRepoIntroducer.EXECUTED_METHODS.isEmpty()
        when:
            bean.aBefore()
            bean.xAfter()
            bean.findAll()
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 3
            MyRepoIntroducer.EXECUTED_METHODS.contains repoDeclaredMethods.find { method -> method.name == "aBefore" }
            MyRepoIntroducer.EXECUTED_METHODS.contains repoDeclaredMethods.find { method -> method.name == "xAfter" }
            MyRepoIntroducer.EXECUTED_METHODS.contains repoDeclaredMethods.find { method -> method.name == "findAll" && method.returnType == List.class }
            MyRepoIntroducer.EXECUTED_METHODS.clear()
    }

    void "test interface overridden method"() {
        when:
            def bean = applicationContext.getBean(CustomCrudRepo)
            def beanDef = applicationContext.getBeanDefinition(CustomCrudRepo)
            def findByIdMethods = beanDef.getExecutableMethods().findAll(m -> m.getName() == "findById")
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 0
            findByIdMethods.size() == 1
            findByIdMethods[0].hasAnnotation(Marker)
        when:
            Object id = 111
            bean.findById(id)
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 1
            MyRepoIntroducer.EXECUTED_METHODS.clear()
        when:
            CrudRepo<Object, Object> crudRepo = bean
            crudRepo.findById(id)
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 1
            MyRepoIntroducer.EXECUTED_METHODS.clear()
    }

    void "test interface abstract overridden method"() {
        when:
            def bean = applicationContext.getBean(AbstractCustomCrudRepo)
            def beanDef = applicationContext.getBeanDefinition(AbstractCustomCrudRepo)
            def findByIdMethods = beanDef.getExecutableMethods().findAll(m -> m.getName() == "findById")
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 0
            findByIdMethods.size() == 1
            findByIdMethods[0].hasAnnotation(Marker)
        when:
            Object id = 111
            bean.findById(id)
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 1
            MyRepoIntroducer.EXECUTED_METHODS.clear()
        when:
            CrudRepo<Object, Object> crudRepo = bean
            crudRepo.findById(id)
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 1
            MyRepoIntroducer.EXECUTED_METHODS.clear()
    }

    void "test abstract overridden method"() {
        when:
            def bean = applicationContext.getBean(AbstractCustomAbstractCrudRepo)
            def beanDef = applicationContext.getBeanDefinition(AbstractCustomAbstractCrudRepo)
            def findByIdMethods = beanDef.getExecutableMethods().findAll(m -> m.getName() == "findById")
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 0
            findByIdMethods.size() == 1
            findByIdMethods[0].hasAnnotation(Marker)
        when:
            def id = 111
            bean.findById(id)
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 1
            MyRepoIntroducer.EXECUTED_METHODS.clear()
        when:
            AbstractCrudRepo<Object, Object> crudRepo = bean
            crudRepo.findById(id)
        then:
            MyRepoIntroducer.EXECUTED_METHODS.size() == 1
            MyRepoIntroducer.EXECUTED_METHODS.clear()
    }

}
