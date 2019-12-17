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

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.builder.AstBuilder
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.inject.annotation.AnnotationMetadataWriter
import io.micronaut.http.annotation.Error
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyAnnotationMetadataBuilderSpec extends Specification {

    void "test enum value action annotation metadata"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata("test.FormController",'badHandler','''\
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
''')
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
        AnnotationMetadata metadata = buildMethodAnnotationMetadata("test.FormController",'simple','''\
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
        metadata != null
        metadata.hasStereotype(Consumes)
        metadata.getValue(Consumes,MediaType[].class).isPresent()
    }

    private AnnotationMetadata buildTypeAnnotationMetadata(String cls, String source) {
        ASTNode[] nodes = new AstBuilder().buildFromString(source)

        ClassNode element = nodes ? nodes.find { it instanceof ClassNode && it.name == cls } : null
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(null, null)
        AnnotationMetadata metadata = element != null ? builder.build(element) : null
        return metadata
    }


    private AnnotationMetadata buildMethodAnnotationMetadata(String cls, String methodName, String source) {
        ASTNode[] nodes = new AstBuilder().buildFromString(source)

        ClassNode element = nodes ? nodes.find { it instanceof ClassNode &&  it.name == cls } : null
        MethodNode method = element.getMethods(methodName)[0]
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(null, null)
        AnnotationMetadata metadata = method != null ? builder.build(method) : null
        return metadata
    }

    protected AnnotationMetadata writeAndLoadMetadata(String className, AnnotationMetadata toWrite) {
        def stream = new ByteArrayOutputStream()
        new AnnotationMetadataWriter(className, toWrite)
                .writeTo(stream)
        className = className + AnnotationMetadata.CLASS_NAME_SUFFIX
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) {
                    def bytes = stream.toByteArray()
                    return defineClass(name, bytes, 0, bytes.length)
                }
                return super.findClass(name)
            }
        }

        AnnotationMetadata metadata = (AnnotationMetadata) classLoader.loadClass(className).newInstance()
        return metadata
    }
}
