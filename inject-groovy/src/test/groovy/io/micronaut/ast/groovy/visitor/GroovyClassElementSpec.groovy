/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.ast.groovy.visitor

import io.micronaut.inject.ast.AbstractClassElementSpec
import io.micronaut.inject.ast.ClassElement
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Issue

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/4424")
class GroovyClassElementSpec extends AbstractClassElementSpec {
    @Override
    protected List<ClassElement> getClassElements() {
        return [new GroovyClassElement(Mock(SourceUnit), null, ClassHelper.OBJECT_TYPE, null),
                new GroovyEnumElement(Mock(SourceUnit), null, ClassHelper.Enum_Type, null)]
    }
}
