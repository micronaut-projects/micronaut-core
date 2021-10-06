package io.micronaut.inject.requires.configprops;

import groovy.lang.Singleton;
import io.micronaut.context.annotation.ConfigurationProperties;


@ConfigurationProperties("inherited")
class InheritedConfig extends OuterConfig {
    private String inheritedProperty;

    public String getInheritedProperty()
    {
        return inheritedProperty;
    }

    public void setInheritedProperty(String inheritedProperty)
    {
        this.inheritedProperty = inheritedProperty;
    }
}

@Singleton
class NotConfigurationProperties {
    private String property;

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }
}

@ConfigurationProperties("outer")
class OuterConfig {

    private String outerProperty;

    public String getOuterProperty() {
        return outerProperty;
    }

    public void setOuterProperty(String outerProperty) {
        this.outerProperty = outerProperty;
    }

    @ConfigurationProperties("inner")
    public static class InnerConfig {

        private String innerProperty;

        public String getInnerProperty() {
            return innerProperty;
        }

        public void setInnerProperty(String innerProperty) {
            this.innerProperty = innerProperty;
        }
    }

}

@ConfigurationProperties("type")
class TypesConfig {

    private String stringProperty;

    private Boolean boolProperty;

    private Integer intProperty;

    public boolean isBoolProperty() {
        return boolProperty;
    }

    public void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty;
    }

    public int getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty;
    }
}
