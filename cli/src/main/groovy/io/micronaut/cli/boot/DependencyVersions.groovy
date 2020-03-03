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
package io.micronaut.cli.boot

import groovy.grape.Grape
import groovy.grape.GrapeEngine
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.slurpersupport.GPathResult
import io.micronaut.cli.util.VersionInfo
import org.springframework.boot.cli.compiler.dependencies.Dependency
import org.springframework.boot.cli.compiler.dependencies.DependencyManagement

/**
 * Introduces dependency management based on a published BOM file
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class DependencyVersions implements DependencyManagement {

    protected Map<String, Dependency> groupAndArtifactToDependency = [:]
    protected Map<String, String> artifactToGroupAndArtifact = [:]
    protected List<Dependency> dependencies = []

    DependencyVersions() {
        this(getDefaultEngine())
    }

    DependencyVersions(Map<String, String> bomCoords) {
        this(getDefaultEngine(), bomCoords)
    }

    DependencyVersions(GrapeEngine grape) {
        this(grape, [group: "io.micronaut", module: "micronaut-bom", version: VersionInfo.getVersion(DependencyVersions), type: "pom"])
    }

    DependencyVersions(GrapeEngine grape, Map<String, String> bomCoords) {
        def results = grape.resolve(null, bomCoords)

        for (URI u in results) {
            def pom = new XmlSlurper().parseText(u.toURL().text)
            addDependencyManagement(pom)
        }
    }

    static GrapeEngine getDefaultEngine() {
        def grape = Grape.getInstance()
        Map<String, Object> resolver = new HashMap<>(1)
        resolver.put("name", "micronautCentral")
        resolver.put("root", "https://repo.micronaut.io")
        grape.addResolver(resolver)
        grape
    }

    @CompileDynamic
    void addDependencyManagement(GPathResult pom) {
        pom.dependencyManagement.dependencies.dependency.each { dep ->
            String version = dep.version.text()
            if (version.startsWith('${')) {
               String property = version[2..-2]
               def child = pom.getProperty("properties").children().find {
                   it.name() == property
               }
               if (child) {
                   version = child.text()
               }
            }
            addDependency(dep.groupId.text(), dep.artifactId.text(), version)
        }
    }

    protected void addDependency(String group, String artifactId, String version) {
        def groupAndArtifactId = "$group:$artifactId".toString()
        artifactToGroupAndArtifact[artifactId] = groupAndArtifactId

        def dep = new Dependency(group, artifactId, version)
        dependencies.add(dep)
        groupAndArtifactToDependency[groupAndArtifactId] = dep
    }

    Dependency find(String groupId, String artifactId) {
        return groupAndArtifactToDependency["$groupId:$artifactId".toString()]
    }

    @Override
    List<Dependency> getDependencies() {
        return dependencies
    }

    @Override
    String getSpringBootVersion() {
        return find("spring-boot").getVersion()
    }

    @Override
    Dependency find(String artifactId) {
        def groupAndArtifact = artifactToGroupAndArtifact[artifactId]
        if (groupAndArtifact)
            return groupAndArtifactToDependency[groupAndArtifact]
    }

    Iterator<Dependency> iterator() {
        return groupAndArtifactToDependency.values().iterator()
    }
}
