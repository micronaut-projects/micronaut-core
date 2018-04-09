/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.http.server.netty

import spock.lang.Specification

class SmartHttpContentCompressorSpec extends Specification {

    private static String compressible = "text/html"
    private static String inCompressible = "image/png"

    void "test should skip"() {
        expect:
        SmartHttpContentCompressor.shouldSkip(type, length) == expected

        where:
        type           | length | expected
        compressible   | 1024   | false     // compressible type and equal to 1k
        compressible   | 1023   | true      // compressible type but smaller than 1k
        compressible   | null   | false     // compressible type but unknown size
        compressible   | 0      | true      // compressible type no content
        inCompressible | 1      | true      // incompressible, always skip
        inCompressible | 5000   | true      // incompressible, always skip
        inCompressible | null   | true      // incompressible, always skip
        inCompressible | 0      | true      // incompressible, always skip
        null           | null   | true      // if the content type is unknown, skip
    }
}
