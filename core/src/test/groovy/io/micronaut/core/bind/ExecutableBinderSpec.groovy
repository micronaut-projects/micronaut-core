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
package io.micronaut.core.bind

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Executable
import io.micronaut.core.version.SemanticVersion
import spock.lang.Requires
import spock.lang.Specification
import spock.util.environment.Jvm

// fails due to https://issues.apache.org/jira/browse/GROOVY-10145
@Requires({
    SemanticVersion.isAtLeastMajorMinor(GroovySystem.version, 4, 0) ||
            !Jvm.current.isJava16Compatible()
})
class ExecutableBinderSpec extends Specification {

    void "test valid executable binder"() {
        given:
        Executable executable = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo")] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }

            @Override
            Class<?> getDeclaringType() {
                return null
            }
        }

        ExecutableBinder binder = new DefaultExecutableBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return { Optional.of(args[1].get(args[0].argument.name)) } as ArgumentBinder.BindingResult
        } )

        registry.findArgumentBinder(_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(executable, registry, [foo:"bar"])

        then:
        bound != null
        bound.invoke(this) == 'bar'
    }

    void "test nullable binder"() {
        given:

        AnnotationMetadata annotationMetadata = Mock(AnnotationMetadata)
        annotationMetadata.hasStereotype(AnnotationUtil.NULLABLE) >> true
        annotationMetadata.getAnnotationTypeByStereotype(_) >> Optional.empty()

        Executable executable = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo", annotationMetadata)] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }

            @Override
            Class<?> getDeclaringType() {
                return null
            }
        }

        ExecutableBinder binder = new DefaultExecutableBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return ArgumentBinder.BindingResult.UNSATISFIED
        } )

        registry.findArgumentBinder(_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(executable, registry, [foo:"bar"])

        then:
        bound != null
        bound.invoke(this) == null
    }

    void "test invalid executable binder"() {
        given:
        Executable executable = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo")] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }

            @Override
            Class<?> getDeclaringType() {
                return null
            }
        }

        ExecutableBinder binder = new DefaultExecutableBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return ArgumentBinder.BindingResult.UNSATISFIED
        } )

        registry.findArgumentBinder(_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(executable, registry, [not:"there"])

        then:
        def e = thrown(UnsatisfiedArgumentException)
        e.message == 'Required argument [String foo] not specified'
    }
}
