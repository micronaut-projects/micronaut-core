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
package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import javax.inject.Named
import javax.validation.constraints.Size

class ArgumentAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test basic argument metadata"() {
        given:
        AnnotationMetadata metadata = buildMethodArgumentAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test {

    void test(@javax.inject.Named("foo") String id) {
    
    }
}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        metadata.hasDeclaredAnnotation(Named)
        metadata.getValue(Named).get() == "foo"
    }

    void "test basic annotation on a byte[] in executable method"() {
        given:
        AnnotationMetadata metadata = buildMethodArgumentAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test {

    @io.micronaut.context.annotation.Executable
    void test(@javax.validation.constraints.Size(max=1024) byte[] id) {
    
    }
}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        metadata.hasDeclaredAnnotation(Size)
    }

    void "test argument metadata inheritance"() {
        given:
        AnnotationMetadata metadata = buildMethodArgumentAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test implements TestApi {

    @javax.annotation.PostConstruct
    @java.lang.Override
    public void test(String id) {
    
    }
}

interface TestApi {

    void test(@javax.inject.Named("foo") String id);

}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        !metadata.hasDeclaredAnnotation(Named)
        metadata.hasAnnotation(Named)
        metadata.getValue(Named).get() == "foo"
    }

}
