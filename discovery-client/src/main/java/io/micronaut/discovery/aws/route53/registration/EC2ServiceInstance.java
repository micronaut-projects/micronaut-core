package io.micronaut.discovery.aws.route53.registration;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpHeaders;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class EC2ServiceInstance implements ServiceInstance, ServiceInstance.Builder {
    String id;
    URI uri;
    HealthStatus healthStatus;
    String instanceId;
    String group;
    String zone;
    String region;
    ConvertibleValues<String> metadata;

    public EC2ServiceInstance(String id, URI uri) {
        this.id = id;

        String userInfo = uri.getUserInfo();
        if(StringUtils.isNotEmpty(userInfo)) {
            try {
                this.uri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                this.metadata = ConvertibleValues.of(Collections.singletonMap(
                        HttpHeaders.AUTHORIZATION_INFO, userInfo
                ));
            } catch (URISyntaxException e) {
                throw new IllegalStateException("ServiceInstance URI is invalid: " + e.getMessage(), e);
            }
        }
        else {
            this.uri = uri;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public Builder instanceId(String id) {
        this.instanceId = id;
        return this;
    }

    @Override
    public Builder zone(String zone) {
        this.zone = zone;
        return this;
    }

    @Override
    public Builder region(String region) {
        this.region = region;
        return this;
    }

    @Override
    public Builder group(String group) {
        this.group = group;
        return this;
    }

    @Override
    public Builder status(HealthStatus status) {
        this.healthStatus = status;
        return this;
    }


    public Builder metadata(ConvertibleValues<String> metadata) {
        this.metadata = metadata;
        return this;
    }

    @Override
    public Builder metadata(Map<String, String> metadata) {
        metadata = metadata;
        return this;
    }

    @Override
    public ServiceInstance build() {
        return this;
    }
}
