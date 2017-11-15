package org.particleframework.inject.configproperties

import org.particleframework.context.annotation.ConfigurationProperties

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
