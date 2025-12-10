package io.micronaut.python.cli.util;

/**
 * Represents a Maven module, without version
 * @param groupId the group id
 * @param artifactId the artifact id
 */
public record MavenModule(
    String groupId,
    String artifactId
) {
}
