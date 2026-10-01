/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * A Java annotation without {@code @Target} may be placed on annotation types, so a Python decorator
 * function carrying only such annotations is an annotation declaration and its stereotypes apply.
 */
class MetaAnnotationTargetSpec extends AbstractPythonTypeElementSpec {

    void "test a python annotation meta-annotated with a java annotation lacking @Target"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Singleton

@Singleton
def ServiceBean(target):
    return target

@ServiceBean
class Impl:
    pass
''')

        when:
        def definition = getBeanDefinition(context, "python.Impl")

        then:
        definition != null
        definition.isSingleton()
        definition.hasDeclaredStereotype("python.ServiceBean")
        definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).contains(AnnotationUtil.SINGLETON)

        cleanup:
        context.close()
    }
}
