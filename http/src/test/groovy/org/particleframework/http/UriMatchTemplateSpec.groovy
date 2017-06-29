/*
 * Copyright 2017 original authors
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
package org.particleframework.http

import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UriMatchTemplateSpec extends Specification {

    void "Test URI matches"() {
        given:
        UriMatchTemplate matchTemplate = new UriMatchTemplate(template)

        expect:
        matchTemplate.matches(uri) == matches

        where:
        template              | uri            | matches
        "/book{/id}"          | '/book/1'      | true
        "/book{/action}{/id}" | '/book/1'      | false
        "/book{/action}{/id}" | '/book/show/1' | true
        "/book{/id}"          | '/book'        | false
        "/book/show{/id}"     | '/book/show/1' | true
        "/book/show{/id}"     | '/book/1'      | false
    }
}
