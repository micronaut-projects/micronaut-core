package io.micronaut.cli.io.support

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency

class BuildTokens {

    protected int getJavaVersion() {
        String version = System.getProperty("java.version")
        if (version.startsWith("1.")) {
            version = version.substring(2)
        }
        // Allow these formats:
        // 1.8.0_72-ea
        // 9-ea
        // 9
        // 9.0.1
        int dotPos = version.indexOf('.')
        int dashPos = version.indexOf('-')
        return Integer.parseInt(version.substring(0,
                dotPos > -1 ? dotPos : dashPos > -1 ? dashPos : version.size()));
    }

    protected String getJdkVersion() {
        String version = System.getProperty("java.version")
        int dotPos = version.indexOf('.')
        int dashPos = version.indexOf('-')

        if (version.startsWith("1.")) {
            dotPos += 2
        }

        return version.substring(0,
                dotPos > -1 ? dotPos : dashPos > -1 ? dashPos : version.size())
    }

    Dependency getAnnotationApi() {
        new Dependency(new DefaultArtifact('javax.annotation:javax.annotation-api:1.3.2'), "compile")
    }

}
