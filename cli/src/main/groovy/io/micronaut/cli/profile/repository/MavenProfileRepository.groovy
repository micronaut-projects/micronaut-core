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
package io.micronaut.cli.profile.repository

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.boot.DependencyVersions
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.util.VersionInfo
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.transfer.ArtifactNotFoundException
import org.springframework.boot.cli.compiler.grape.AetherGrapeEngine
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext
import org.springframework.boot.cli.compiler.grape.DependencyResolutionFailedException

/**
 *  Resolves profiles from a configured list of repositories using Aether
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class MavenProfileRepository extends AbstractJarProfileRepository {

    public static final RepositoryConfiguration DEFAULT_REPO
    public static final RepositoryConfiguration SNAPSHOT_REPO = new RepositoryConfiguration(
            "micronautSnapshots",
            new URI("https://oss.jfrog.org/artifactory/oss-snapshot-local"), true
    )
    public static final RepositoryConfiguration RELEASE_REPO = new RepositoryConfiguration(
            "jcenter",
            new URI("https://jcenter.bintray.com"), true
    )

    static {
        def version = VersionInfo.getVersion(MicronautCli)

        if (version == null || version.endsWith("-SNAPSHOT")) {
            DEFAULT_REPO = SNAPSHOT_REPO
        } else {
            DEFAULT_REPO = RELEASE_REPO
        }
    }


    List<RepositoryConfiguration> repositoryConfigurations
    AetherGrapeEngine grapeEngine
    GroovyClassLoader classLoader
    DependencyResolutionContext resolutionContext
    DependencyVersions profileDependencyVersions
    private boolean resolved = false
    private String mavenLocalLocation

    MavenProfileRepository(List<RepositoryConfiguration> repositoryConfigurations) {
        this.repositoryConfigurations = repositoryConfigurations
        classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader)
        resolutionContext = new DependencyResolutionContext()
        this.grapeEngine = AetherGrapeEngineFactory.create(classLoader, repositoryConfigurations, resolutionContext)
        profileDependencyVersions = new DependencyVersions(grapeEngine)
        resolutionContext.addDependencyManagement(profileDependencyVersions)
    }

    MavenProfileRepository() {
        this([DEFAULT_REPO])
    }

    @Override
    @CompileDynamic
    String findVersion(String artifactId) {
        return profileDependencyVersions.find(artifactId)?.version
    }

    @Override
    Profile getProfile(String profileName, Boolean parentProfile) {
        String profileShortName = profileName
        if (profileName.contains(':')) {
            def art = new DefaultArtifact(profileName)
            profileShortName = art.artifactId
        }
        if (!profilesByName.containsKey(profileShortName)) {
            if (parentProfile && profileDependencyVersions.find(DEFAULT_PROFILE_GROUPID, profileShortName)) {
                return resolveProfile(profileShortName)
            } else {
                return resolveProfile(profileName)
            }
        }
        return super.getProfile(profileShortName)
    }

    @Override
    Profile getProfile(String profileName) {
        getProfile(profileName, false)
    }

    protected Profile resolveProfile(String profileName) {
        Artifact art = getProfileArtifact(profileName)

        try {
            grapeEngine.grab(group: art.groupId,
                    module: art.artifactId,
                    version: art.version ?: null)
        } catch (ArtifactNotFoundException | DependencyResolutionFailedException e) {
            MicronautConsole.instance.addStatus("Profile $art could not be resolved remotely. Searching maven local...")
            def localData = new File(mavenLocal, "/${art.groupId.replace('.', '/')}/$art.artifactId/maven-metadata-local.xml")
            if (localData.exists()) {
                def currentVersion = parseCurrentVersion(localData)
                def profileFile = new File(localData.parentFile, "$currentVersion/${art.artifactId}-${currentVersion}.jar")
                if (profileFile.exists()) {
                    classLoader.addURL(profileFile.toURI().toURL())
                } else {
                    MicronautConsole.instance.error("${profileFile} not found in ${localData}")
                    throw e
                }
            } else {
                MicronautConsole.instance.error("Also: ${localData} not found")
                throw e
            }
        }

        processUrls()
        return super.getProfile(art.artifactId)
    }

    @CompileDynamic
    protected String parseCurrentVersion(File localData) {
        new XmlSlurper().parse(localData).versioning.versions.version[0].text()
    }

    protected void processUrls() {
        def urls = classLoader.getURLs()
        for (URL url in urls) {
            registerProfile(url, new URLClassLoader([url] as URL[], Thread.currentThread().contextClassLoader))
        }
    }

    @CompileDynamic
    protected String getMavenLocal() {
        if(!mavenLocalLocation) {

            File settingsXml = new File(System.getProperty("user.home"), "/.m2/settings.xml")
            if(settingsXml.exists()) {
                String localRepo = new XmlSlurper().parseText(settingsXml.text)?.localRepository
                if(localRepo) {
                    mavenLocalLocation = "${localRepo.replace('${user.home}', System.getProperty("user.home"))}"
                }
            }

            if(!mavenLocalLocation) {
                mavenLocalLocation = "${System.getProperty("user.home")}/.m2/repository/"
            }
        }

        mavenLocalLocation
    }

    @Override
    List<Profile> getAllProfiles() {
        if (!resolved) {
            List<Map> profiles = []
            resolutionContext.managedDependencies.each { Dependency dep ->
                if (dep.artifact.groupId == "io.micronaut.profiles") {
                    profiles.add([group: dep.artifact.groupId, module: dep.artifact.artifactId])
                }
            }
            profiles.sort { it.module }

            List<String> unresolved = []
            for (Map profile in profiles) {
                try {
                    grapeEngine.grab(profile)
                } catch (ArtifactNotFoundException | DependencyResolutionFailedException e) {
                    if (profile.module) {
                        unresolved.add(profile.module.toString())
                        if (Boolean.getBoolean("micronaut.verbose")) {
                            MicronautConsole.instance.warn("Ignoring error: " + e)
                        }
                    }
                }
            }

            if (unresolved) {
                MicronautConsole.instance.addStatus("Not all profiles could be resolved remotely. Searching Maven local...")
                def localData = new File(mavenLocal, "/io/micronaut/profiles")
                if (localData.exists()) {
                    localData.eachDir { File dir ->
                        if (unresolved.contains(dir.name)) {
                            def profileData = new File(dir, "/maven-metadata-local.xml")
                            if (profileData.exists()) {
                                def currentVersion = parseCurrentVersion(profileData)
                                def profileFile = new File(dir, "$currentVersion/${dir.name}-${currentVersion}.jar")
                                if (profileFile.exists()) {
                                    classLoader.addURL(profileFile.toURI().toURL())
                                }
                            }
                        }
                    }
                }
            }


            processUrls()
            resolved = true
        }
        return super.getAllProfiles()
    }
}
