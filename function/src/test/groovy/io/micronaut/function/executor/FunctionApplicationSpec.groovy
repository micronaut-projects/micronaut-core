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
package io.micronaut.function.executor

import io.micronaut.function.LocalFunctionRegistry
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.nio.charset.StandardCharsets

@RestoreSystemProperties
class FunctionApplicationSpec extends Specification {


    void "run supplier function"() {
        given:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "supplier")
        System.out = new PrintStream(out)

        when:
        FunctionApplication.main()

        then:
        new String(out.toByteArray(), StandardCharsets.UTF_8).endsWith 'myvalue'

        cleanup:
        System.out = oldOut
    }

    void "run regular function"() {
        given:
        def oldOut = System.out
        def oldIn = System.in

        def out = new ByteArrayOutputStream()
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "round")
        System.out = new PrintStream(out)
        System.in = new ByteArrayInputStream("100.4".bytes)

        when:
        FunctionApplication.main()

        then:
        new String(out.toByteArray(), StandardCharsets.UTF_8).endsWith '100'

        cleanup:
        System.out = oldOut
        System.in = oldIn
    }

    void "run POJO function"() {
        given:
        def oldOut = System.out
        def oldIn = System.in

        def out = new ByteArrayOutputStream()
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "upper")
        System.out = new PrintStream(out)
        System.in = new ByteArrayInputStream('{"name":"fred"}'.bytes)

        when:
        FunctionApplication.main()

        then:
        new String(out.toByteArray(), StandardCharsets.UTF_8).endsWith '{"name":"FRED"}'

        cleanup:
        System.out = oldOut
        System.in = oldIn
    }
}
