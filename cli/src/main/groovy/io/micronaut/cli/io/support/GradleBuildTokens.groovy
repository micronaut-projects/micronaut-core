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
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.repository.MavenProfileRepository
import io.micronaut.cli.util.VersionInfo
import org.eclipse.aether.graph.Dependency

/**
 * @author James Kleeh
 * @sicen 1.0
 */
@InheritConstructors
class GradleBuildTokens extends BuildTokens {

    Map getTokens(ProfileRepository profileRepository, Profile profile, List<Feature> features) {
        Map tokens = [:]
        tokens.put("testFramework", testFramework)
        tokens.put("sourceLanguage", sourceLanguage)

        def ln = System.getProperty("line.separator")

        Closure repositoryUrl = { int spaces, String repo ->
            repo.startsWith('http') ? "${' ' * spaces}maven { url \"${repo}\" }" : "${' ' * spaces}${repo}"
        }

        String defaultRepo = MavenProfileRepository.DEFAULT_REPO.uri.toString()

        def repositories = (profile.repositories + defaultRepo).collect(repositoryUrl.curry(4)).unique().join(ln)

        List<GradleDependency> dependencies = getDependencies(profile, features)

        String dependencyString = dependencies.sort({ GradleDependency dep -> dep.scope }).collect() { GradleDependency dep ->
            dep.toString(4)
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

        tokens.put("jarPath", "build/libs/$appname-*-all.jar")
        tokens.put("jvmArgs", jvmArgs)
        tokens.put("buildPlugins", buildPlugins)
        tokens.put("dependencies", dependencyString)
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

    protected List<GradleDependency> getDependencies(Profile profile, List<Feature> features) {
        List<Dependency> dependencies = super.materializeDependencies(profile, features)
        List<GradleDependency> gradleDependencies = []
        final String enforcedPlatform = ' platform("io.micronaut:micronaut-bom:$micronautVersion")'
        gradleDependencies.add(new GradleDependency("implementation", enforcedPlatform))
        gradleDependencies.add(new GradleDependency("testImplementation", enforcedPlatform))
        if (sourceLanguage == "groovy") {
            gradleDependencies.add(new GradleDependency("compileOnly", enforcedPlatform))
            gradleDependencies.add(new GradleDependency("testCompileOnly", enforcedPlatform))
        } else if (sourceLanguage == "java") {
            gradleDependencies.add(new GradleDependency("annotationProcessor", enforcedPlatform))
            gradleDependencies.add(new GradleDependency("testAnnotationProcessor", enforcedPlatform))
        } else if (sourceLanguage == "kotlin") {
            gradleDependencies.add(new GradleDependency("kapt", enforcedPlatform))
            gradleDependencies.add(new GradleDependency("kaptTest", enforcedPlatform))
        }
        gradleDependencies.addAll(dependencies.collect{
            if (it.scope == 'annotationProcessor' && sourceLanguage == 'kotlin') {
                new GradleDependency("kapt", it)
            } else if (it.scope == 'testAnnotationProcessor' && sourceLanguage == 'kotlin') {
                new GradleDependency("kaptTest", it)
            } else {
                return new GradleDependency(it)
            }
        })
        gradleDependencies
    }
}
