package io.micronaut.inject.configproperties.eachbeanparameter;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.value.PropertyCatalog;

@Requires(property = "spec", value = "DisabledNormalizationSpec")
@EachProperty(value = "app.normalized", catalog = PropertyCatalog.NORMALIZED)
public class EachPropertyPropertiesNormalized {

    private String name;
    private String url;
    private int serverPort;

    @ConfigurationInject
    public EachPropertyPropertiesNormalized(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
