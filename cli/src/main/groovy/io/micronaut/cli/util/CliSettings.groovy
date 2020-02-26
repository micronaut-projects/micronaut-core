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
package io.micronaut.cli.util

import groovy.transform.CompileStatic

/**
 * CLI settings and configuration
 *
 * @author Graeme Rocher
 */
@CompileStatic
class CliSettings {

    /**
     * The http proxy username
     */
    public static final String PROXY_HTTP_USER = "http.proxyUser"
    /**
     * The http proxy password
     */
    public static final String PROXY_HTTP_PASSWORD = "http.proxyPassword"
    /**
     * The proxy selector object to use when connecting remotely from the CLI
     */
    public static final String PROXY_SELECTOR = "micronaut.proxy.selector"
    /**
     * The authenticator to use when connecting remotely from the CLI
     */
    public static final String AUTHENTICATOR = "micronaut.proxy.authenticator"
    /**
     * Name of the System property that specifies the main class name
     */
    public static final String MAIN_CLASS_NAME = "io.micronaut.cli.MAIN_CLASS_NAME"

    /**
     * The name of the profile being used
     */
    public static final String PROFILE = "profile"
    /**
     * Specifies the profile repositories to use
     */
    public static final String PROFILE_REPOSITORIES = "micronaut.profiles.repositories"

    /**
     * The base directory of the application
     */
    public static final String APP_BASE_DIR = "base.dir"

    /**
     * The name of the system property for the project classes directory. Must be set if changed from build/main/classes.
     */
    public static final String PROJECT_CLASSES_DIR = "micronaut.project.class.dir"

    /**
     * The base directory of the project
     */
    public static final File BASE_DIR = new File('.')

    /**
     * Whether the application is running inside the development environment or deployed
     */
    public static final boolean MICRONAUIT_APP_DIR_PRESENT

    /**
     * The target directory of the project, null outside of the development environment
     */
    public static final File TARGET_DIR

    /**
     * The classes directory of the project, null outside of the development environment
     */
    public static final File CLASSES_DIR;

    /**
     * The path to the build classes directory
     */
    public static final String BUILD_CLASSES_PATH


    public static final File SETTINGS_FILE = new File("${System.getProperty('user.home')}/.micronaut/settings.groovy")

}
