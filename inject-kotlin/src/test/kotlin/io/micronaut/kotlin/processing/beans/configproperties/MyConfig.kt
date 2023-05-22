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
package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.ReadableBytes
import java.net.URL
import java.util.*

@ConfigurationProperties("foo.bar")
open class MyConfig {
    var port = 0
    var defaultValue = 9999
    var stringList: List<String>? = null
    var intList: List<Int>? = null
    var urlList: List<URL>? = null
    var urlList2: List<URL>? = null
    var emptyList: List<URL>? = null
    var flags: Map<String, Int>? = null
    var url: Optional<URL>? = null
    var anotherUrl = Optional.empty<URL>()
    var inner: Inner? = null
    var defaultPort = 9999
        protected set
    var anotherPort: Int? = null
        protected set
    var innerVals: List<InnerVal>? = null

    @ReadableBytes
    var maxSize = 0

    @ReadableBytes
    var anotherSize = 0
    var map: Map<String, Map<String, Value>> = HashMap()

    class Value {
        var property = 0
        var property2: Value2? = null

        constructor() {}
        constructor(property: Int, property2: Value2?) {
            this.property = property
            this.property2 = property2
        }
    }

    class Value2 {
        var property = 0

        constructor() {}
        constructor(property: Int) {
            this.property = property
        }
    }

    @ConfigurationProperties("inner")
    class Inner {
        var enabled = false
        fun isEnabled(): Boolean {
            return enabled
        }
    }
}

class InnerVal {
    var expireUnsignedSeconds: Int? = null
}
