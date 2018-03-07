package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties('foo.baz')
class MyOtherConfig extends ParentPojo {

    String otherProperty

    private String temp

    void setOnlySetter(String value) {
        temp = value
    }

    String getOnlySetter() {
        temp
    }
}
