package io.micronaut.docs.http.client.bind.type;

public class Metadata {

    private final Double version;
    private final Long deploymentId;

    public Metadata(Double version, Long deploymentId) {
        this.version = version;
        this.deploymentId = deploymentId;
    }

    public Double getVersion() {
        return version;
    }

    public Long getDeploymentId() {
        return deploymentId;
    }
}
