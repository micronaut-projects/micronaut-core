/*
 * Copyright 2017 original authors
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
package io.micronaut.gradle.publish

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskContainer

/**
 * A plugin to setup publishing to Micronaut central repo
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MicronautCentralPublishGradlePlugin implements Plugin<Project> {

    String getErrorMessage(String missingSetting) {
        return """No '$missingSetting' was specified. Please provide a valid publishing configuration. Example:

micronautPublish {
    user = 'user'
    key = 'key'
    userOrg = 'my-company' // optional, otherwise published to personal bintray account
    repo = 'plugins' // optional, defaults to 'plugins'


    websiteUrl = 'http://foo.com/myplugin'
    license {
        name = 'Apache-2.0'
    }
    issueTrackerUrl = 'http://github.com/myname/myplugin/issues'
    vcsUrl = 'http://github.com/myname/myplugin'
    title = "My plugin title"
    desc = "My plugin description"
    developers = [johndoe:"John Doe"]
}

or

micronautPublish {
    user = 'user'
    key = 'key'
    githubSlug = 'foo/bar'
    license {
        name = 'Apache-2.0'
    }
    title = "My plugin title"
    desc = "My plugin description"
    developers = [johndoe:"John Doe"]
}

Your publishing user and key can also be placed in PROJECT_HOME/gradle.properties or USER_HOME/gradle.properties. For example:

bintrayUser=user
bintrayKey=key

Or using environment variables:

BINTRAY_USER=user
BINTRAY_KEY=key
"""
    }

    @Override
    void apply(Project project) {
        project.plugins.apply(MavenPublishPlugin)

        ExtensionContainer extensionContainer = project.extensions
        TaskContainer taskContainer = project.tasks
        PublishExtension publishExtension = extensionContainer.create("micronautPublish", PublishExtension)


        def bintraySiteUrl = project.hasProperty('websiteUrl') ? project.websiteUrl : ""
        def bintrayIssueTrackerUrl = project.hasProperty('issueTrackerUrl') ? project.issueTrackerUrl : ""
        def bintrayVcsUrl = project.hasProperty('vcsUrl') ? project.vcsUrl : ""
        def bintrayLicense = project.hasProperty('license') ? [project.license] : []
        def bintrayOrg = project.hasProperty('userOrg') ? project.userOrg : ''
        def signingPassphrase = System.getenv("SIGNING_PASSPHRASE") ?: project.hasProperty("signingPassphrase") ? project.signingPassphrase : ''
        def bintrayUser = System.getenv("BINTRAY_USER") ?: project.hasProperty("bintrayUser") ? project.bintrayUser : ''
        def bintrayKey = System.getenv("BINTRAY_KEY") ?: project.hasProperty("bintrayKey") ? project.bintrayKey : ''
        def sonatypeOssUsername = System.getenv("SONATYPE_USERNAME") ?: project.hasProperty("sonatypeOssUsername") ? project.sonatypeOssUsername : ''
        def sonatypeOssPassword = System.getenv("SONATYPE_PASSWORD") ?: project.hasProperty("sonatypeOssPassword") ? project.sonatypeOssPassword : ''
        def bintrayRepo = project.hasProperty('repo') ? project.repo : ''
        def bintrayDescription = project.hasProperty('desc') ? project.desc : ""

        def configurer = {
            BintrayExtension bintrayExtension = extensionContainer.findByType(BintrayExtension)

            if(publishExtension.mavenCentralSync) {
                bintrayExtension.pkg.version.mavenCentralSync.sync = true
            }
            if(publishExtension.gpgSign) {
                bintrayExtension.pkg.version.gpg.sign = true
            }
            if(publishExtension.user) {
                bintrayExtension.user = publishExtension.user
            }
            if(publishExtension.repo) {
                bintrayExtension.pkg.repo = publishExtension.repo
            }
            else if(!bintrayExtension.pkg.repo) {
                bintrayExtension.pkg.repo = getDefaultRepo()
            }
            if(publishExtension.desc) {
                bintrayExtension.pkg.desc = publishExtension.desc
            }
            else if(!bintrayExtension.pkg.desc) {
                bintrayExtension.pkg.desc = getDefaultDescription(project)
            }
            if(publishExtension.key) {
                bintrayExtension.key = publishExtension.key
            }
            if(publishExtension.userOrg) {
                bintrayExtension.pkg.userOrg = publishExtension.userOrg
            }
            else {
                bintrayExtension.pkg.userOrg = ''
            }

            if(publishExtension.websiteUrl) {
                bintrayExtension.pkg.websiteUrl = publishExtension.websiteUrl
            }
            else if(publishExtension.githubSlug) {
                bintrayExtension.pkg.websiteUrl = "https://github.com/$publishExtension.githubSlug"
            }

            if(publishExtension.vcsUrl) {
                bintrayExtension.pkg.vcsUrl = publishExtension.vcsUrl
            }
            else if(publishExtension.githubSlug) {
                bintrayExtension.pkg.vcsUrl = "https://github.com/$publishExtension.githubSlug"
            }

            if(publishExtension.issueTrackerUrl) {
                bintrayExtension.pkg.issueTrackerUrl = publishExtension.issueTrackerUrl
            }
            else if(publishExtension.githubSlug) {
                bintrayExtension.pkg.issueTrackerUrl = "https://github.com/$publishExtension.githubSlug/issues"
            }

            if(publishExtension.license?.name) {
                bintrayExtension.pkg.licenses = [publishExtension.license.name] as String[]
            }

            if(publishExtension.signingPassphrase) {
                bintrayExtension.pkg.version.gpg.passphrase = publishExtension.signingPassphrase
            }
            if(publishExtension.sonatypeOssUsername) {
                bintrayExtension.pkg.version.mavenCentralSync.user = publishExtension.sonatypeOssUsername
            }
            if(publishExtension.sonatypeOssPassword) {
                bintrayExtension.pkg.version.mavenCentralSync.password = publishExtension.sonatypeOssPassword
            }

            def pkgVersion = bintrayExtension.pkg.version.name
            if(!pkgVersion || pkgVersion == 'unspecified') {
                bintrayExtension.pkg.version.name = project.version
            }
        }

        def username = System.getenv('MICRONAUT_CENTRAL_USERNAME') ?: project.hasProperty('micronautCentralUsername') ? project.micronautCentralUsername : ''
        def password = System.getenv("MICRONAUT_CENTRAL_PASSWORD") ?: project.hasProperty('micronautCentralPassword') ? project.micronautCentralPassword : ''

        project.plugins.apply(BintrayPlugin)

        project.afterEvaluate(configurer)

        project.publishing {
            publications {
                maven(MavenPublication) {
                    pom.withXml {
                        Node pomNode = asNode()
                        PublishExtension gpe = project.extensions.findByType(PublishExtension)
                        boolean centralPublishEnabled = bintrayKey ?: gpe.key

                        if(pomNode.dependencyManagement) {
                            pomNode.dependencyManagement[0].replaceNode {}
                        }

                        if(gpe != null) {
                            pomNode.children().last() + {
                                def title = gpe.title ?: project.name
                                delegate.name title
                                delegate.description gpe.desc ?: title

                                def websiteUrl = gpe.websiteUrl ?: gpe.githubSlug ? "https://github.com/$gpe.githubSlug" : ''
                                if(!websiteUrl && centralPublishEnabled) {
                                    throw new RuntimeException(getErrorMessage('websiteUrl'))
                                }

                                delegate.url websiteUrl


                                def license = gpe.license
                                if(license != null) {

                                    def concreteLicense = PublishExtension.License.LICENSES.get(license.name)
                                    if(concreteLicense != null) {

                                        delegate.licenses {
                                            delegate.license {
                                                delegate.name concreteLicense.name
                                                delegate.url concreteLicense.url
                                                delegate.distribution concreteLicense.distribution
                                            }
                                        }
                                    }
                                    else if(license.name && license.url )  {
                                        delegate.licenses {
                                            delegate.license {
                                                delegate.name license.name
                                                delegate.url license.url
                                                delegate.distribution license.distribution
                                            }
                                        }
                                    }
                                }
                                else if(centralPublishEnabled) {
                                    throw new RuntimeException(getErrorMessage('license'))
                                }

                                if(gpe.githubSlug) {
                                    delegate.scm {
                                        delegate.url "https://github.com/$gpe.githubSlug"
                                        delegate.connection "scm:git@github.com:${gpe.githubSlug}.git"
                                        delegate.developerConnection "scm:git@github.com:${gpe.githubSlug}.git"
                                    }
                                    delegate.issueManagement {
                                        delegate.system "Github Issues"
                                        delegate.url "https://github.com/$gpe.githubSlug/issues"
                                    }
                                }
                                else {
                                    if(gpe.vcsUrl) {
                                        delegate.scm {
                                            delegate.url gpe.vcsUrl
                                            delegate.connection "scm:$gpe.vcsUrl"
                                            delegate.developerConnection "scm:$gpe.vcsUrl"
                                        }
                                    }
                                    else if(centralPublishEnabled) {
                                        throw new RuntimeException(getErrorMessage('vcsUrl'))
                                    }

                                    if(gpe.issueTrackerUrl) {
                                        delegate.issueManagement {
                                            delegate.system "Issue Tracker"
                                            delegate.url gpe.issueTrackerUrl
                                        }
                                    }
                                    else if(centralPublishEnabled) {
                                        throw new RuntimeException(getErrorMessage('issueTrackerUrl'))
                                    }

                                }

                                if(gpe.developers) {

                                    delegate.developers {
                                        for(entry in gpe.developers.entrySet()) {
                                            delegate.developer {
                                                delegate.id entry.key
                                                delegate.name entry.value
                                            }
                                        }
                                    }
                                }
                                else if(centralPublishEnabled) {
                                    throw new RuntimeException(getErrorMessage('developers'))
                                }
                            }

                        }

                        // simply remove dependencies without a version
                        // version-less dependencies are handled with dependencyManagement
                        // see https://github.com/spring-gradle-plugins/dependency-management-plugin/issues/8 for more complete solutions
                        pomNode.dependencies.dependency.findAll {
                            it.version.text().isEmpty()
                        }.each {
                            it.replaceNode {}
                        }
                    }
                    artifactId project.name
                    from project.components.java
                    def sourcesJar = taskContainer.findByName("sourcesJar")
                    if(sourcesJar != null) {
                        artifact sourcesJar
                    }
                    def javadocJar = taskContainer.findByName("javadocJar")
                    if(javadocJar != null) {
                        artifact javadocJar
                    }
                    def extraArtefact = getDefaultExtraArtifact(project)
                    if(extraArtefact) {
                        artifact extraArtefact
                    }
                }
            }


            if(username && password) {

                repositories {
                    maven {
                        credentials {
                            username username
                            password password
                        }

                        if(project.version.toString().endsWith('-SNAPSHOT')) {
                            url getDefaultSnapshotRepo()
                        }
                        else {
                            url getDefaultReleaseRepo()
                        }
                    }
                }
            }

        }


        project.bintray {
            user = bintrayUser
            key = bintrayKey
            publications = ['maven']
            publish = true
            pkg {
                repo = bintrayRepo
                userOrg = bintrayOrg
                name = project.name
                desc = bintrayDescription
                websiteUrl = bintraySiteUrl
                issueTrackerUrl = bintrayIssueTrackerUrl
                vcsUrl = bintrayVcsUrl

                licenses = bintrayLicense
                publicDownloadNumbers = true
                version {
                    def artifactType = getDefaultArtifactType()
                    attributes = [(artifactType): "$project.group:$project.name"]
                    name = project.version
                    gpg {
                        sign = false
                        passphrase = signingPassphrase
                    }
                    mavenCentralSync {
                        sync = false
                        user = sonatypeOssUsername
                        password = sonatypeOssPassword
                    }
                }
            }
        }

        def installTask = taskContainer.findByName("install")
        def bintrayUploadTask = taskContainer.findByName('bintrayUpload')
        if(bintrayUploadTask != null) {
            String className = defaultClassifier.substring(0,1).toUpperCase(Locale.ENGLISH) + defaultClassifier.substring(1)
            taskContainer.create(name:"publish${className}", dependsOn: bintrayUploadTask)
        }
        if(installTask == null) {
            def publishToMavenLocal = taskContainer.findByName("publishToMavenLocal")
            if(publishToMavenLocal != null) {
                taskContainer.create(name:"install", dependsOn: publishToMavenLocal)
            }
        }
    }

    protected String getDefaultArtifactType() {
        "micronaut-$defaultClassifier"
    }

    protected String getDefaultReleaseRepo() {
        "https://repo.micronaut.io/artifactory/configurations-releases"
    }

    protected String getDefaultSnapshotRepo() {
        "https://repo.micronaut.io/artifactory/configurations-snapshots"
    }

    protected Map<String, String> getDefaultExtraArtifact(Project project) {
        null
    }

    protected String getDefaultClassifier() {
        "configuration"
    }

    protected String getDefaultDescription(Project project) {
        "Micronaut ${project.name} $defaultClassifier"
    }

    protected String getDefaultRepo() {
        "configurations"
    }
}
