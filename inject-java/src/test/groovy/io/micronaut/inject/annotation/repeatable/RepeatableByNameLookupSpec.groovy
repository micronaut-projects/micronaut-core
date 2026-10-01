/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.annotation.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition

import java.lang.annotation.Annotation

/**
 * Looking a repeatable annotation up by name must find the first repeated value, exactly like the
 * {@code Class} overload does. A repeatable is stored under its container, so the plain map lookup
 * these queries do misses it — even for a single {@code @Q("a")}, which is wrapped too.
 */
class RepeatableByNameLookupSpec extends AbstractTypeElementSpec {

    private static final String Q = 'repeatablebyname.Q'

    private static final String SOURCE = '''
package repeatablebyname;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

@Singleton
@Q("a")
@Q("b")
class Multi {
}

@Singleton
@Q("a")
class Single {
}

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@Repeatable(Qs.class)
@interface Q {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Qs {
    Q[] value();
}
'''

    void "test finding a repeatable annotation by name at compile time"() {
        given:
        AnnotationMetadata multi = buildClassElement(SOURCE).getAnnotationMetadata()

        expect:
        multi.findAnnotation(Q).get().stringValue().get() == 'a'
        multi.findDeclaredAnnotation(Q).get().stringValue().get() == 'a'
        multi.getAnnotation(Q).stringValue().get() == 'a'
        multi.getDeclaredAnnotation(Q).stringValue().get() == 'a'
        multi.stringValue(Q).get() == 'a'
    }

    void "test finding a repeatable annotation by name in the generated definition"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('repeatablebyname.Single', SOURCE)
        Class<? extends Annotation> q = definition.getBeanType().getClassLoader().loadClass(Q) as Class<? extends Annotation>
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        expect: 'the Class overload is the reference'
        metadata.findAnnotation(q).get().stringValue().get() == 'a'

        and: 'the name must answer the same'
        metadata.findAnnotation(Q).get().stringValue().get() == 'a'
        metadata.findDeclaredAnnotation(Q).get().stringValue().get() == 'a'
    }
}
