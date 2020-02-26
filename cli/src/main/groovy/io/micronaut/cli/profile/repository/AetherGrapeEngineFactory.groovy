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
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.internal.impl.DefaultRepositorySystem
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.spi.locator.ServiceLocator
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.springframework.boot.cli.compiler.grape.AetherGrapeEngine
import org.springframework.boot.cli.compiler.grape.DefaultRepositorySystemSessionAutoConfiguration
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext
import org.springframework.boot.cli.compiler.grape.RepositorySystemSessionAutoConfiguration

/**
 *  Creates aether engine to resolve profiles. Mostly copied from {@link AetherGrapeEngine}.
 *  Created to support repositories with authentication.
 *
 * @author James Kleeh
 * @since 1.0
 */
@CompileStatic
class AetherGrapeEngineFactory {

    static AetherGrapeEngine create(GroovyClassLoader classLoader,
                                    List<RepositoryConfiguration> repositoryConfigurations,
                                    DependencyResolutionContext dependencyResolutionContext) {

        RepositorySystem repositorySystem = createServiceLocator()
            .getService(RepositorySystem.class)

        DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils
            .newSession()

        ServiceLoader<RepositorySystemSessionAutoConfiguration> autoConfigurations = ServiceLoader
            .load(RepositorySystemSessionAutoConfiguration.class)

        for (RepositorySystemSessionAutoConfiguration autoConfiguration : autoConfigurations) {
            autoConfiguration.apply(repositorySystemSession, repositorySystem)
        }

        new DefaultRepositorySystemSessionAutoConfiguration()
            .apply(repositorySystemSession, repositorySystem)

        return new AetherGrapeEngine(classLoader, repositorySystem,
                                     repositorySystemSession, createRepositories(repositoryConfigurations),
                                     dependencyResolutionContext, false)
    }

    private static ServiceLocator createServiceLocator() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositorySystem.class, DefaultRepositorySystem.class)
        locator.addService(RepositoryConnectorFactory.class,
                           BasicRepositoryConnectorFactory.class)
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class)
        locator.addService(TransporterFactory.class, FileTransporterFactory.class)
        return locator
    }

    private static List<RemoteRepository> createRepositories(
        List<RepositoryConfiguration> repositoryConfigurations) {
        List<RemoteRepository> repositories = new ArrayList<RemoteRepository>(
            repositoryConfigurations.size())
        for (RepositoryConfiguration repositoryConfiguration : repositoryConfigurations) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder(
                repositoryConfiguration.getName(), "default",
                repositoryConfiguration.getUri().toASCIIString())
            if (repositoryConfiguration.hasCredentials()) {
                builder.authentication = new AuthenticationBuilder()
                    .addUsername(repositoryConfiguration.username)
                    .addPassword(repositoryConfiguration.password)
                    .build()
            }
            if (!repositoryConfiguration.getSnapshotsEnabled()) {
                builder.setSnapshotPolicy(
                    new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER,
                                         RepositoryPolicy.CHECKSUM_POLICY_IGNORE))
            }
            repositories.add(builder.build())
        }
        return repositories
    }
}
