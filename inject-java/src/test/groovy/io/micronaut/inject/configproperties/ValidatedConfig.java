package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;
import org.hibernate.validator.constraints.NotBlank;
import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import java.net.URL;

@ConfigurationProperties("foo.bar")
public class ValidatedConfig {
    @NotNull
    URL url;

    @NotBlank
    protected String name;

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
