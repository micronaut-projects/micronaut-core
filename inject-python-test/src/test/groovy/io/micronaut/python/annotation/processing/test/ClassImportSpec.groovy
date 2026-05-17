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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.beans.BeanIntrospectionReference

class ClassImportSpec extends AbstractPythonTypeElementSpec {

    void "test class import annotates Java classes with introspected"() {
        given:
        def context = buildContext('''
from micronaut.context.annotation import ClassImport

@ClassImport(
    classNames=[
        "io.micronaut.python.annotation.processing.test.classimport.ClassImportBean",
        "io.micronaut.python.annotation.processing.test.classimport.ClassImportInterface"
    ],
    annotateNames=["io.micronaut.core.annotation.Introspected"]
)
class Application:
    pass
''')

        when:
        BeanIntrospectionReference beanReference = context.classLoader
            .loadClass("python.\$io_micronaut_python_annotation_processing_test_classimport_ClassImportBean\$Introspection")
            .newInstance()
        BeanIntrospectionReference interfaceReference = context.classLoader
            .loadClass("python.\$io_micronaut_python_annotation_processing_test_classimport_ClassImportInterface\$Introspection")
            .newInstance()

        then:
        beanReference != null
        interfaceReference != null

        cleanup:
        context?.close()
    }
}
