package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyConfigInner {

    List<InnerVal> innerVals

    static class InnerVal {
        Integer expireUnsignedSeconds
    }

}

