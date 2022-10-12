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
package io.micronaut.ast.groovy.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Error
import io.micronaut.inject.BeanDefinition

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyAnnotationMetadataBuilderSpec extends AbstractBeanDefinitionSpec {

    void "test enum value action annotation metadata"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata("test.FormController", '''\
package test;

import io.micronaut.web.router.annotation.*
import io.micronaut.http.*
import io.micronaut.http.annotation.*

@Controller(consumes = MediaType.APPLICATION_FORM_URLENCODED)
class FormController {
   @Error(status=HttpStatus.BAD_REQUEST)
    HttpResponse badHandler() {
        HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff")
    }
}
''', 'badHandler')
        when:
        metadata = writeAndLoadMetadata("test.FormController",  metadata)

        then:
        metadata != null
        metadata.hasStereotype(Error)
        !metadata.isPresent(Error.class, "exception")
        !metadata.isPresent(Error.class, "value")
        metadata.isPresent(Error.class, "status")
        metadata.getValue(Error,"status", HttpStatus).get() == HttpStatus.BAD_REQUEST
    }


    void "test controller action annotation metadata"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("test.FormController", '''\
package test;

import io.micronaut.web.router.annotation.*
import io.micronaut.http.*
import io.micronaut.http.annotation.*

@Controller(consumes = MediaType.APPLICATION_FORM_URLENCODED)
class FormController {
    @Post
    String simple(String name, Integer age) {
        "name: $name, age: $age"
    }
}
''')

        expect:
        beanDefinition.getExecutableMethods().size() == 1 != null
        beanDefinition.getExecutableMethods().iterator().next().hasStereotype(Consumes)
        beanDefinition.getExecutableMethods().iterator().next().getValue(Consumes,MediaType[].class).isPresent()
    }
}
