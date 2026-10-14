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

import io.micronaut.inject.ast.ClassElement
import io.micronaut.python.annotation.processing.test.javabases.Plugins

/**
 * The class element of a Python class extending a Java class is assignable to every supertype of the
 * Java base: its superclasses and the interfaces they implement, as a Java subclass would be.
 */
class JavaBaseAssignabilitySpec extends AbstractPythonTypeElementSpec {

    void "a Python class extending a Java class is assignable to the supertypes of the base"() {
        expect:
        buildClassElement('''
from micronaut.python.annotation.processing.test.javabases import Plugins


class UpperPlugin(Plugins.AbstractPlugin[str]):
    def configure(self, value: str) -> str:
        return value.upper()


class SubPlugin(UpperPlugin):
    pass
''', "SubPlugin") { ClassElement classElement ->
            for (ClassElement element : [classElement, classElement.getSuperType().get()]) {
                assert element.isAssignable(Plugins.AbstractPlugin)
                assert element.isAssignable(Plugins.NamedPlugin)
                assert element.isAssignable(Plugins.Plugin)
                assert element.isAssignable(Plugins.Named)
                assert element.isAssignable(Object)
                assert !element.isAssignable(CharSequence)
            }
            def typeArguments = classElement.getAllTypeArguments()
            assert typeArguments[Plugins.AbstractPlugin.name]['T'].name == String.name
            assert typeArguments[Plugins.Plugin.name]['T'].name == String.name
            return classElement
        }
    }
}
