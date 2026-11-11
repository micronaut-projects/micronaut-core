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
package io.micronaut.http.server

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class ExecutableRouteInfoSpec extends Specification {

    void "the route of an exception handler method answers whether there is a target method as the method does"() {
        given:
        ExecutableMethod<Object, Object> method = [
                getDeclaringType     : { Object },
                getMethodName        : { 'handle' },
                getArguments         : { Argument.ZERO_ARGUMENTS },
                getReturnType        : { ReturnType.of(Object) },
                getAnnotationMetadata: { AnnotationMetadata.EMPTY_METADATA },
                getTargetMethod      : { throw new AssertionError('not looked up') },
                hasTargetMethod      : { hasTarget }
        ] as ExecutableMethod<Object, Object>

        expect:
        new ExecutableRouteInfo<>(method, true).hasTargetMethod() == hasTarget

        where:
        hasTarget << [true, false]
    }
}
