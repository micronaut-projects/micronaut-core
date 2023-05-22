package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyConfigInner {
    var innerVals: List<InnerVal>? = null

    class InnerVal {
        var expireUnsignedSeconds: Int? = null
    }
}
