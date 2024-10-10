package io.micronaut.inject.configproperties.eachbeanparameter;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.value.PropertyCatalog;

@Requires(property = "spec", value = "DisabledNormalizationSpec")
@EachProperty(value = "app.generated", catalog = PropertyCatalog.GENERATED)
public class EachPropertyPropertiesGenerated {

    private String name;
    private String url;
    private int serverPort;

    @ConfigurationInject
    public EachPropertyPropertiesGenerated(@Parameter String name) {
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
