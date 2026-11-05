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
package io.micronaut.inject.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import spock.lang.Specification

class ConverterAsLambdaSpec extends Specification {

    void "list converter beans retain their target type arguments"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'genericListConverters'])

        expect:
        ctx.conversionService.convert('1;2', Argument.listOf(Integer)).get() == [1, 2]
        ctx.conversionService.convert('FOO;BAR', Argument.listOf(TestConverters.Status)).get() == [TestConverters.Status.FOO, TestConverters.Status.BAR]
        ctx.conversionService.convert('a,b', Argument.listOf(String)).get() == ['a', 'b']
        ctx.conversionService.convert('3;4', Argument.listOf(Integer)).get() == [3, 4]
        ctx.conversionService.convert('a,b', List).get() == ['a', 'b']

        cleanup:
        ctx.close()
    }

    void "test converter registered via lambda"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.conversionService.convert("foo", TestConverters.Foo).isPresent()

        cleanup:
        ctx.close()
    }
}
