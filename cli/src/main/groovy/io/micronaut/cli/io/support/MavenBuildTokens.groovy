/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.io.support

import groovy.transform.InheritConstructors
import groovy.xml.MarkupBuilder
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.repository.MavenProfileRepository
import io.micronaut.cli.util.VersionInfo
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion

/**
 * @author James Kleeh
 * @since 1.0
 */
@InheritConstructors
class MavenBuildTokens extends BuildTokens {

    public static Map<String, String> scopeConversions = [:]

    static {
        scopeConversions.put("compile", "compile")
        scopeConversions.put("implementation", "compile")
        scopeConversions.put("provided", "provided")
        scopeConversions.put("runtime", "runtime")
        scopeConversions.put("runtimeOnly", "runtime")
        scopeConversions.put("compileOnly", "provided")
        scopeConversions.put("testRuntime", "test")
        scopeConversions.put("testImplementation", "test")
        scopeConversions.put("testCompile", "test")
        scopeConversions.put("testCompileOnly", "test")
    }

    @Override
    Map getTokens(ProfileRepository profileRepository, Profile profile, List<Feature> features) {
        Map tokens = [:]
        tokens.put("testFramework", testFramework)
        tokens.put("sourceLanguage", sourceLanguage)
        
        def ln = System.getProperty("line.separator")

        def repositoriesWriter = new StringWriter()
        MarkupBuilder repositoriesXml = new MarkupBuilder(repositoriesWriter)
        String defaultRepo = MavenProfileRepository.DEFAULT_REPO.uri.toString()

        (profile.repositories + defaultRepo).each { String repo ->
            if (repo.startsWith('http')) {
                try {
                    URI uri = URI.create(repo)
                    if (uri != null) {
                        repositoriesXml.repository {
                            id(uri.host)
                            url(repo)
                        }
                    }
                } catch (Exception e) {
                    //no-op
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

        List<Dependency> dependencies = materializeDependencies(profile, features)
        List<Dependency> annotationProcessors = dependencies
                .unique()
                .findAll( { it.scope == 'annotationProcessor' || it.scope == 'kapt' })

        annotationProcessors.find {
            if (it.artifact.artifactId == 'micronaut-picocli') {
                annotationProcessors.swap(annotationProcessors.indexOf(it), annotationProcessors.size() - 1)
            }
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
                if (dep.exclusions != null && !dep.exclusions.empty) {
                    exclusions {
                        dep.exclusions.each { Exclusion e ->
                            exclusion {
                                groupId(e.groupId)
                                artifactId(e.artifactId)
                            }
                        }
                    }
                }
            }
        }

        def jvmArgsWriter = new StringWriter()
        MarkupBuilder jvmArgsXml = new MarkupBuilder(jvmArgsWriter)

        def arguments = profile.jvmArgs
        for (Feature f in features) {
            arguments.addAll(f.jvmArgs)
        }

        arguments.each { String arg ->
            jvmArgsXml.argument("${arg}")
        }

        def annotationProcessorsWriter = new StringWriter()
        MarkupBuilder annotationProcessorPathsXml = new MarkupBuilder(annotationProcessorsWriter)
        annotationProcessors = annotationProcessors.sort { Dependency dep1, Dependency dep2 ->
            def g1 = dep1.artifact.groupId
            if (g1 == 'org.projectlombok') {
                // lombok always first
                return -1
            } else {
                def g2 = dep2.artifact.groupId
                if (g1 == g2 ){
                    return 0
                } else if (g1 == 'io.micronaut' && g2 != 'io.micronaut') {
                    return -1
                } else {
                    return 1
                }
            }
        }
        annotationProcessors.each { Dependency dep ->
            def artifact = dep.artifact
            String methodToCall = sourceLanguage == 'kotlin' ? 'annotationProcessorPath' : 'path'
            annotationProcessorPathsXml."$methodToCall" {
                groupId(artifact.groupId)
                artifactId(artifact.artifactId)
                def artifactVersion = artifact.version
                if (!artifactVersion || artifactVersion == 'BOM') {
                    def resolvedVersion = profileRepository.findVersion(artifact.artifactId)
                    if (resolvedVersion ) {
                        artifactVersion = resolvedVersion
                    }
                }
                if (artifact.groupId.startsWith("io.micronaut")) {
                    if (!artifactVersion || artifactVersion == 'BOM') {
                        version("\${micronaut.version}")
                    } else {
                        version(artifactVersion)
                    }
                } else {
                    version(artifactVersion)
                }
            }
        }


        tokens.put("jarPath", "target/$appname-*.jar" )
        tokens.put("arguments", prettyPrint(jvmArgsWriter.toString(), 12))
        tokens.put("dependencies", prettyPrint(dependenciesWriter.toString(), 4))
        tokens.put("repositories", prettyPrint(repositoriesWriter.toString(), 4))
        tokens.put("jdkversion", VersionInfo.getJdkVersion())
        tokens.put("annotationProcessorPaths", prettyPrint(annotationProcessorsWriter.toString(), 18))

        tokens
    }

    @Override
    Map getTokens(List<String> services) {
        final StringWriter modulesWriter = new StringWriter()
        MarkupBuilder modulesXml = new MarkupBuilder(modulesWriter)

        services.each { String name ->
            modulesXml.module(name)
        }

        ["services": prettyPrint(modulesWriter.toString(), 4)]
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
