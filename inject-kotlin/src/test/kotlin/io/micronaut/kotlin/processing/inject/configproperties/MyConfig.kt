package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.ReadableBytes

import java.net.URL
import java.util.Optional

@ConfigurationProperties("foo.bar")
class MyConfig {
    var port: Int = 0
    var defaultValue: Int = 9999
    var stringList: List<String>? = null
    var intList: List<Int>? = null
    var urlList: List<URL>? = null
    var urlList2: List<URL>? = null
    var emptyList: List<URL>? = null
    var flags: Map<String,Int>? = null
    var url: Optional<URL>? = null
    var anotherUrl: Optional<URL> = Optional.empty()
    var inner: Inner? = null
    protected var defaultPort: Int = 9999
    protected var anotherPort: Int? = null
    var innerVals: List<InnerVal>? = null

    @ReadableBytes
    var maxSize: Int = 0

    var map: Map<String, Map<String, Value>> = mapOf()

    class Value {
        var property: Int = 0
        var property2: Value2? = null

        constructor()

        constructor(property: Int, property2: Value2) {
            this.property = property
            this.property2 = property2
        }
    }

    class Value2 {
        var property: Int = 0

        constructor()

        constructor(property: Int) {
            this.property = property
        }
    }

    @ConfigurationProperties("inner")
    class Inner {
        var enabled = false

        fun isEnabled() = enabled
    }

}

class InnerVal {
    var expireUnsignedSeconds: Int? = null
}
