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
package io.micronaut.kotlin.processing.beans.aliasfor

import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

import spock.lang.Specification

class AliasForQualifierSpec extends Specification {

    void "test that when an alias is created for a named qualifier the stereotypes are correct"() {
        given:
        BeanDefinition definition = KotlinCompiler.buildBeanDefinition('test.Test$MyFunc0','''\
package test

import io.micronaut.context.annotation.Factory
import io.micronaut.kotlin.processing.beans.aliasfor.TestAnnotation

@Factory
class Test {

    @TestAnnotation("foo")
    fun myFunc(): (String) -> Int {
        return { str -> 10 }
    }
}

''')
        expect:
        definition != null
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).isPresent()
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == AnnotationUtil.NAMED
        definition.getValue(AnnotationUtil.NAMED, String).get() == 'foo'
    }
}
