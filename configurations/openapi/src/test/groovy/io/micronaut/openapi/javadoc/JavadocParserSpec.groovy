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
package io.micronaut.openapi.javadoc

import spock.lang.Specification

class JavadocParserSpec extends Specification {

    void 'test parse basic javadoc'() {

        given:
        JavadocParser parser = new JavadocParser()
        JavadocDescription desc = parser.parse('''
This is a description with <b>bold</b> and {@code some code} 

@since 1.0
@param foo The foo param
@param bar The {@code bar} param
@return The {@value return} value
''')


        expect:
        desc.methodDescription
        desc.returnDescription
        desc.parameters.size() == 2
        desc.methodDescription == 'This is a description with bold and some code'
        desc.returnDescription == 'The return value'
        desc.parameters['foo'] == 'The foo param'

    }

    void 'test parse multiline javadoc'() {

        given:
        JavadocParser parser = new JavadocParser()
        JavadocDescription desc = parser.parse('''
<p>This is a description with <b>bold</b> and {@code some code}.</p>

And more stuff.

@since 1.0
@param foo The foo param
@param bar The {@code bar} param
@return The {@value return} value
''')


        expect:
        desc.methodDescription
        desc.returnDescription
        desc.parameters.size() == 2
        desc.methodDescription == '''This is a description with bold and some code.

And more stuff.'''
        desc.returnDescription == 'The return value'
        desc.parameters['foo'] == 'The foo param'

    }
}
