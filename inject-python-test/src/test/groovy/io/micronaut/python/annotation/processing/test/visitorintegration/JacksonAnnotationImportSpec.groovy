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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * A Jackson 3 {@code JsonNaming} import: the class valued member names a nested Java class, and an annotation
 * mapper resolves that class through the visitor context while the Python class is being registered.
 */
class JacksonAnnotationImportSpec extends AbstractPythonTypeElementSpec {

    void "test a Jackson 3 JsonNaming annotation with a class valued member is imported and mapped"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass

from micronaut.core.annotation import Introspected
from tools.jackson.databind import PropertyNamingStrategies
from tools.jackson.databind.annotation import JsonNaming


@Introspected
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
@dataclass
class GithubUser:
    login: str | None = None
    name: str | None = None
''')

        when:
        def introspection = getBeanIntrospection(context, "python.GithubUser")

        then:
        introspection != null
        introspection.hasAnnotation(JsonNaming)
        introspection.getAnnotation(JsonNaming).annotationClassValue("value").get().name == PropertyNamingStrategies.SnakeCaseStrategy.name
        introspection.stringValue(NamingStrategy).get() == "SnakeCaseStrategy"
        introspection.instantiate("octocat", "The Octocat").login == "octocat"

        cleanup:
        context?.close()
    }
}
