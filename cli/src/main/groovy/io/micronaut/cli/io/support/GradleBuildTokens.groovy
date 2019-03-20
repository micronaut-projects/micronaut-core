/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.repository.MavenProfileRepository
import io.micronaut.cli.util.VersionInfo
import org.eclipse.aether.graph.Dependency

/**
 * @author James Kleeh
 * @sicen 1.0
 */
@InheritConstructors
class GradleBuildTokens extends BuildTokens {

    static final SCOPE_MAP = [
            compile: 'compile',
            runtime: 'runtime',
            testCompile: 'testCompile',
            provided: 'developmentOnly'
    ]

    Map getTokens(Profile profile, List<Feature> features) {
        Map tokens = [:]
        tokens.put("testFramework", testFramework)
        tokens.put("sourceLanguage", sourceLanguage)

        def ln = System.getProperty("line.separator")

        Closure repositoryUrl = { int spaces, String repo ->
            repo.startsWith('http') ? "${' ' * spaces}maven { url \"${repo}\" }" : "${' ' * spaces}${repo}"
        }

        String defaultRepo = MavenProfileRepository.DEFAULT_REPO.uri.toString()

        def repositories = (profile.repositories + defaultRepo).collect(repositoryUrl.curry(4)).unique().join(ln)

        List<Dependency> profileDependencies = profile.dependencies
        def dependencies = profileDependencies.findAll() { Dependency dep ->
            dep.scope != 'build'
        }

        for (Feature f in features) {
            dependencies.addAll f.dependencies.findAll() { Dependency dep -> dep.scope != 'build' }
        }

        dependencies = dependencies.unique()

        dependencies = dependencies.sort({ Dependency dep -> dep.scope }).collect() { Dependency dep ->
            String scope = SCOPE_MAP.get(dep.scope)
            if (scope == null) scope = dep.scope
            String artifactStr = resolveArtifactString(dep, 4)
            "    ${scope}${artifactStr}".toString()
        }.unique().join(ln)

        def buildPlugins = profile.buildPlugins.collect() { String name ->
            def nameAndVersion = name.split(":")
            if (nameAndVersion.length == 2) {
                "    id \"${nameAndVersion[0]}\" version \"${nameAndVersion[1]}\""
            } else {
                "    id \"${name}\""
            }
        }

        def jvmArgs = profile.jvmArgs
        for (Feature f in features) {
            jvmArgs.addAll(f.jvmArgs)
        }

        jvmArgs = jvmArgs.collect { String arg -> "'${arg}'"}.join(',')

        for (Feature f in features) {
            buildPlugins.addAll f.buildPlugins.collect() { String name ->
                def nameAndVersion = name.split(":")
                if (nameAndVersion.length == 2) {
                    "    id \"${nameAndVersion[0]}\" version \"${nameAndVersion[1]}\""
                } else {
                    "    id \"${name}\""
                }
            }
        }

        buildPlugins = buildPlugins.unique()

        String buildDependencies = buildPlugins.findAll({!it.startsWith("apply")}).join(ln)
        buildPlugins = buildPlugins.findAll({it.startsWith("apply")}).join(ln)

        tokens.put("jarPath", "build/libs/$appname-*.jar")
        tokens.put("jvmArgs", jvmArgs)
        tokens.put("buildPlugins", buildPlugins)
        tokens.put("dependencies", dependencies)
        tokens.put("buildDependencies", buildDependencies)
        tokens.put("repositories", repositories)
        tokens.put("jdkversion", VersionInfo.getJdkVersion())

        tokens
    }

    Map getTokens(List<String> services) {
        final String serviceString = services.collect { String name ->
            "include \'$name\'"
        }.join(System.getProperty("line.separator"))

        ["services": serviceString]
    }

    protected String resolveArtifactString(Dependency dep, int spaces) {
        def artifact = dep.artifact
        def v = artifact.version.replace('BOM', '')
        StringBuilder artifactString = new StringBuilder()
        if (dep.exclusions != null && !dep.exclusions.empty) {
            artifactString.append('(')
        } else {
            artifactString.append(' ')
        }
        artifactString.append('"')
        artifactString.append(artifact.groupId)
        artifactString.append(':').append(artifact.artifactId)
        if (v) {
            artifactString.append(':').append(v)
        }
        artifactString.append('"')

        def ln = System.getProperty("line.separator")

        if (dep.exclusions != null && !dep.exclusions.empty) {
            artifactString.append(") {").append(ln)
            for (e in dep.exclusions) {
                artifactString.append(" " * (spaces)).append("    ")
                    .append("exclude")

                artifactString.append(" group: ").append('"').append(e.groupId).append('",')
                artifactString.append(" module: ").append('"').append(e.artifactId).append('"')

                artifactString.append(ln)
            }
            artifactString.append(" " * spaces).append("}")
        }
        return artifactString.toString()
    }
}
