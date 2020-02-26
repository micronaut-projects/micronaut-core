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

import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.io.support.ClassPathResource
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.profile.AbstractProfile
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProjectContext
import io.micronaut.cli.profile.ProjectContextAware
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency

/**
 * A repository that loads profiles from JAR files
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractJarProfileRepository implements ProfileRepository {

    protected final List<Profile> allProfiles = []
    protected final Map<String, Profile> profilesByName = [:]
    protected static final String DEFAULT_PROFILE_GROUPID = "io.micronaut.profiles"

    private Set<URL> registeredUrls = []

    @Override
    Profile getProfile(String profileName) {
        return profilesByName[profileName]
    }

    @Override
    String findVersion(String artifactId) {
        return null
    }

    @Override
    Profile getProfile(String profileName, Boolean parentProfile) {
        return getProfile(profileName)
    }

    List<Profile> getAllProfiles() {
        return allProfiles
    }

    @Override
    Resource getProfileDirectory(String profile) {
        return getProfile(profile)?.profileDir
    }

    @Override
    List<Profile> getProfileAndDependencies(Profile profile) {
        List<Profile> sortedProfiles = []
        Set<Profile> visitedProfiles = [] as Set
        visitTopologicalSort(profile, sortedProfiles, visitedProfiles)
        return sortedProfiles
    }

    Artifact getProfileArtifact(String profileName) {
        if (profileName.contains(':')) {
            return new DefaultArtifact(profileName)
        }

        String groupId = DEFAULT_PROFILE_GROUPID
        String version = null

        Map<String, Map> defaultValues = MicronautCli.getSetting("profiles", Map, [:])
        defaultValues.remove("repositories")
        def data = defaultValues.get(profileName)
        if (data instanceof Map) {
            groupId = data.get("groupId")
            version = data.get("version")
        }

        return new DefaultArtifact(groupId, profileName, null, version)
    }

    protected void registerProfile(URL url, ClassLoader parent) {
        if (registeredUrls.contains(url)) return

        def classLoader = new URLClassLoader([url] as URL[], parent)
        def profileYml = classLoader.getResource("META-INF/profile/profile.yml")
        if (profileYml != null) {
            registeredUrls.add(url)
            def profile = new JarProfile(this, new ClassPathResource("META-INF/profile/", classLoader), classLoader)
            profile.profileRepository = this
            allProfiles.add profile
            profilesByName[profile.name] = profile
        }
    }

    private void visitTopologicalSort(Profile profile, List<Profile> sortedProfiles, Set<Profile> visitedProfiles) {
        if (profile != null && !visitedProfiles.contains(profile)) {
            visitedProfiles.add(profile)
            profile.getExtends().each { Profile dependentProfile ->
                visitTopologicalSort(dependentProfile, sortedProfiles, visitedProfiles);
            }
            sortedProfiles.add(profile)
        }
    }

    static class JarProfile extends AbstractProfile {

        JarProfile(ProfileRepository repository, Resource profileDir, ClassLoader classLoader) {
            super(profileDir, classLoader)
            this.profileRepository = repository
            initialize()
        }

        @Override
        String getName() {
            super.name
        }

        @Override
        Iterable<Command> getCommands(ProjectContext context) {
            super.getCommands(context)
            for (cmd in internalCommands) {
                if (cmd instanceof ProjectContextAware) {
                    ((ProjectContextAware) cmd).setProjectContext(context)
                }
                commandsByName[cmd.name] = cmd
            }

            return commandsByName.values()
        }
    }

}
