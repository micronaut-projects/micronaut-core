package io.micronaut.cli.io.support

import groovy.xml.MarkupBuilder
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import org.eclipse.aether.graph.Dependency

class MavenBuildTokens {

    public static Map<String, String> scopeConversions = [:]

    static {
        scopeConversions.put("compile", "compile")
        scopeConversions.put("runtime", "runtime")
        scopeConversions.put("compileOnly", "provided")
        scopeConversions.put("testRuntime", "test")
        scopeConversions.put("testCompile", "test")
        scopeConversions.put("testCompileOnly", "test")
    }

    Map getTokens(Profile profile, List<Feature> features) {
        Map tokens = [:]

        def ln = System.getProperty("line.separator")

        def repositoriesWriter = new StringWriter()
        MarkupBuilder repositoriesXml = new MarkupBuilder(repositoriesWriter)
        profile.repositories.each { String repo ->
            if (repo.startsWith('http')) {
                repositoriesXml.repository {
                    id(repo.replaceAll("^http(|s)://(.*?)/.*", '$2'))
                    url(repo)
                }
            } else if (repo == 'jcenter()') {
                repositoriesXml.repository {
                    id('jcenter')
                    url("https://jcenter.bintray.com/")
                }
            } else if (repo == 'google()') {
                repositoriesXml.repository {
                    id('google')
                    url("https://dl.google.com/dl/android/maven2/")
                }
            }
        }

        List<Dependency> profileDependencies = profile.dependencies
        List<Dependency> dependencies = profileDependencies.findAll() { Dependency dep ->
            dep.scope != 'build'
        }

        for (Feature f in features) {
            dependencies.addAll f.dependencies.findAll() { Dependency dep -> dep.scope != 'build' }
        }

        dependencies = dependencies.unique()
                .findAll { scopeConversions.containsKey(it.scope) }
                .collect { convertScope(it) }
                .sort { it.scope }
                .groupBy { it.artifact }
                .collect { k, deps -> deps.size() == 1 ? deps.first() : resolveScopeDuplicate(deps) }

        def dependenciesWriter = new StringWriter()
        MarkupBuilder dependenciesXml = new MarkupBuilder(dependenciesWriter)
        dependencies.each { Dependency dep ->

            def artifact = dep.artifact
            def v = artifact.version.replace('BOM', '')
            dependenciesXml.dependency {
                groupId(artifact.groupId)
                artifactId(artifact.artifactId)
                if (v) {
                    version(artifact.version)
                }
                scope(dep.scope)
            }
        }

        tokens.put("dependencies", prettyPrint(dependenciesWriter.toString(), 8))
        tokens.put("repositories", prettyPrint(repositoriesWriter.toString(), 8))

        tokens
    }

    Map getTokens(List<String> services) {
        final StringWriter modulesWriter = new StringWriter()
        MarkupBuilder modulesXml = new MarkupBuilder(modulesWriter)

        services.each { String name ->
            modulesXml.module(name)
        }

        ["services": prettyPrint(modulesWriter.toString(), 8)]
    }
  
    Dependency convertScope(Dependency dependency) {
        dependency.setScope(scopeConversions.getOrDefault(dependency.scope, dependency.scope))
    }

    Dependency resolveScopeDuplicate(List<Dependency> dependencies) {
        dependencies.find { it.scope == 'compile' } ?:
                dependencies.find { it.scope == 'provided' } ?:
                        dependencies.find { it.scope = 'runtime' } ?:
                                dependencies.find { it.scope = 'test' }

    }

    String prettyPrint(String xml, int spaces) {
        xml.replaceAll("(?m)^", " " * spaces)
    }
}
