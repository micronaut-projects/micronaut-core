package org.particleframework.inject.configproperties.inheritance;

import org.particleframework.context.annotation.ConfigurationProperties;

@ConfigurationProperties("foo.baz")
public class MyOtherConfig extends ParentPojo {

    private String otherProperty;
    private String temp;

    public void setOnlySetter(String value) {
        temp = value;
    }

    public String getOnlySetter() {
        return temp;
    }

    public String getOtherProperty() {
        return otherProperty;
    }

    public void setOtherProperty(String otherProperty) {
        this.otherProperty = otherProperty;
    }

}
