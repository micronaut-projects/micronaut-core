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
package io.micronaut.json.bind

import io.micronaut.context.ApplicationContext
import io.micronaut.core.bind.BeanPropertyBinder
import io.micronaut.core.convert.exceptions.ConversionErrorException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Nested indexed form keys must be bounded in depth and in aggregate array padding.
 */
class NestedFormKeyLimitsSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    private static String key(int segments) {
        return "a[99]." * segments + "x"
    }

    void "a deeply nested key does not overflow the stack"() {
        given:
        BeanPropertyBinder binder = context.getBean(BeanPropertyBinder)

        when:
        binder.bind(Map, [(key(6000)): "1"].entrySet())

        then:
        thrown(ConversionErrorException)
    }

    void "a nested indexed key does not amplify allocation"() {
        given:
        BeanPropertyBinder binder = context.getBean(BeanPropertyBinder)

        when:
        binder.bind(Map, [(key(50)): "1"].entrySet())

        then:
        thrown(ConversionErrorException)
    }

    void "sequential indexes are still bound"() {
        given:
        BeanPropertyBinder binder = context.getBean(BeanPropertyBinder)
        def source = (0..<50).collectEntries { [("a[" + it + "].x"): "v" + it] }

        when:
        Map result = binder.bind(Map, source.entrySet())

        then:
        result.a.size() == 50
        result.a[49].x == "v49"
    }
}
