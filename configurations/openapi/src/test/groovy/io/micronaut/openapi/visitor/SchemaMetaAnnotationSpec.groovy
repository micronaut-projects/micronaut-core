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
package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.UUIDSchema
import spock.lang.Specification

class SchemaMetaAnnotationSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "true")
    }

    def cleanup() {
        System.setProperty(AbstractOpenApiVisitor.ATTR_TEST_MODE, "")
    }

    void "test default UUID handling"() {


        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.*;
import com.fasterxml.jackson.annotation.*;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/pets")
interface MyOps {

    @Post("/")
    String save(java.util.UUID uuid);
}

@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        PathItem pathItem = openAPI.paths.get("/pets")

        then:"it is included in the OpenAPI doc"
        pathItem.post.operationId == 'save'
        pathItem.post.requestBody
        pathItem.post.requestBody.required
        pathItem.post.requestBody.content
        pathItem.post.requestBody.content.size() == 1
        pathItem.post.requestBody.content['application/json'].schema
        pathItem.post.requestBody.content['application/json'].schema.type == 'object'
        pathItem.post.requestBody.content['application/json'].schema.properties.size() == 1
        pathItem.post.requestBody.content['application/json'].schema.properties['uuid']
        pathItem.post.requestBody.content['application/json'].schema.properties['uuid'] instanceof UUIDSchema
        pathItem.post.requestBody.content['application/json'].schema.properties['uuid'].type == 'string'
        pathItem.post.requestBody.content['application/json'].schema.properties['uuid'].format == 'uuid'
    }

    void "test that it possible to define custom schema annotations at the type level"() {


        given:"An API definition"
        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.reactivex.*;
import io.micronaut.http.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.*;
import com.fasterxml.jackson.annotation.*;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author graemerocher
 * @since 1.0
 */

@Controller("/pets")
interface MyOps {

    @Post("/")
    String save(@MyAnn java.util.UUID uuid);
}

@Documented
@Retention(RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Schema(type="string", format="uuid")
@interface MyAnn {

}
@javax.inject.Singleton
class MyBean {}
''')
        then:"the state is correct"
        AbstractOpenApiVisitor.testReference != null

        when:"The OpenAPI is retrieved"
        OpenAPI openAPI = AbstractOpenApiVisitor.testReference
        PathItem pathItem = openAPI.paths.get("/pets")

        then:"it is included in the OpenAPI doc"
        pathItem.post.operationId == 'save'
        pathItem.post.requestBody
        pathItem.post.requestBody.required
        pathItem.post.requestBody.content
        pathItem.post.requestBody.content.size() == 1
        pathItem.post.requestBody.content['application/json'].schema
        pathItem.post.requestBody.content['application/json'].schema.type == 'object'
        pathItem.post.requestBody.content['application/json'].schema.properties.size() == 1
        pathItem.post.requestBody.content['application/json'].schema.properties['uuid']
        pathItem.post.requestBody.content['application/json'].schema.properties['uuid'].$ref  == '#/components/schemas/MyAnn'

        openAPI.components.schemas['MyAnn']
        openAPI.components.schemas['MyAnn'].type == 'string'
        openAPI.components.schemas['MyAnn'].format == 'uuid'
    }
}
