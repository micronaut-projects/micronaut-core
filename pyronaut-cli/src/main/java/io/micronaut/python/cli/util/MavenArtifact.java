package io.micronaut.python.cli.util;

/**
 * Represents a Maven artifact.
 * @param module the module id
 * @param version the version
 */
public record MavenArtifact(
    MavenModule module,
    String version
) {

    public MavenArtifact(
        String groupId,
        String artifactId,
        String version
    ) {
        this(
            new MavenModule(groupId, artifactId),
            version
        );
    }

    public String groupId() {
        return module.groupId();
    }

    public String artifactId() {
        return module.artifactId();
    }
}
