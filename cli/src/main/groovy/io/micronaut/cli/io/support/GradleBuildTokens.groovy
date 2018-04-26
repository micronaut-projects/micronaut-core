package io.micronaut.cli.io.support

import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import org.eclipse.aether.graph.Dependency

class GradleBuildTokens {

    Map getTokens(Profile profile, List<Feature> features) {
        Map tokens = [:]

        def ln = System.getProperty("line.separator")

        Closure repositoryUrl = { int spaces, String repo ->
            repo.startsWith('http') ? "${' ' * spaces}maven { url \"${repo}\" }" : "${' ' * spaces}${repo}"
        }

        def repositories = profile.repositories.collect(repositoryUrl.curry(4)).unique().join(ln)

        List<Dependency> profileDependencies = profile.dependencies
        def dependencies = profileDependencies.findAll() { Dependency dep ->
            dep.scope != 'build'
        }
        def buildDependencies = profileDependencies.findAll() { Dependency dep ->
            dep.scope == 'build'
        }

        for(Feature f in features) {
            dependencies.addAll f.dependencies.findAll(){ Dependency dep -> dep.scope != 'build'}
            buildDependencies.addAll f.dependencies.findAll(){ Dependency dep -> dep.scope == 'build'}
        }

        dependencies = dependencies.unique()

        dependencies = dependencies.sort({ Dependency dep -> dep.scope }).collect() { Dependency dep ->
            String artifactStr = resolveArtifactString(dep)
            "    ${dep.scope} \"${artifactStr}\"".toString()
        }.unique().join(ln)

        def buildRepositories = profile.buildRepositories.collect(repositoryUrl.curry(8)).unique().join(ln)

        buildDependencies = buildDependencies.collect() { Dependency dep ->
            String artifactStr = resolveArtifactString(dep)
            "        classpath \"${artifactStr}\"".toString()
        }.unique().join(ln)

        def buildPlugins = profile.buildPlugins.collect() { String name ->
            "apply plugin:\"$name\""
        }

        for(Feature f in features) {
            buildPlugins.addAll f.buildPlugins.collect() { String name ->
                "apply plugin:\"$name\""
            }
        }

        buildPlugins = buildPlugins.unique().join(ln)

        tokens.put("buildPlugins", buildPlugins)
        tokens.put("dependencies", dependencies)
        tokens.put("buildDependencies", buildDependencies)
        tokens.put("buildRepositories", buildRepositories)
        tokens.put("repositories", repositories)

        tokens
    }

    Map getTokens(List<String> services) {
        final String serviceString = services.collect { String name ->
            "include \'$name\'"
        }.join(System.getProperty("line.separator"))

        ["services": serviceString]
    }

    protected String resolveArtifactString(Dependency dep) {
        def artifact = dep.artifact
        def v = artifact.version.replace('BOM', '')

        return v ? "${artifact.groupId}:${artifact.artifactId}:${v}" : "${artifact.groupId}:${artifact.artifactId}"
    }
}
