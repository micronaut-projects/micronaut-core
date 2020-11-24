package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected

@ConfigurationProperties("foo.bar")
class MyConfigInner {

    List<InnerVal> innerVals

    @Introspected
    static class InnerVal {
        Integer expireUnsignedSeconds
    }

}

