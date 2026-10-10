/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.ResourceRoot;
import io.micronaut.logging.impl.LogbackUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configures Logback from the application's resources. Logback lives in the parent tier, and its own lookup at
 * startup searches the loader that loaded it, which does not see the application's resources: only a generation's
 * loader does. Without this, the application's {@code logback.xml} is never read under the launcher, and Logback keeps
 * its basic configuration, which logs everything at DEBUG.
 *
 * <p>The launcher configures Logback from the manifest's resources before it logs anything, and at INFO when they hold
 * no configuration, then before the first generation starts, from that generation's loader, unless it finds the
 * configuration Logback already has, and whenever a Logback configuration file among the resources changes. Nothing is
 * done when Logback is not the SLF4J provider.</p>
 *
 * @since 5.3.0
 */
@Internal
final class DevLogging {

    private static final String LOGGER_CONTEXT = "ch.qos.logback.classic.LoggerContext";
    // Logback's own names, spelled out: this class is loaded whether Logback is present or not
    private static final String CONFIG_FILE_PROPERTY = "logback.configurationFile";
    private static final String AUTOCONFIG_FILE = "logback.xml";
    private static final String TEST_AUTOCONFIG_FILE = "logback-test.xml";
    private static final Logger LOG = LoggerFactory.getLogger(DevLogging.class);

    private volatile boolean stale = true;
    private volatile boolean changed;

    /**
     * Configures Logback from the manifest's resource and reloadable roots, before the launcher logs anything: the
     * application's {@code logback.xml} applies from the first line, and when there is none the launcher logs at INFO
     * until the first generation configures Logback as the application's own startup would.
     *
     * @param manifest The manifest
     * @param parent   The loader of the parent tier
     */
    static void beforeLaunch(DevManifest manifest, ClassLoader parent) {
        if (!isLogback()) {
            return;
        }
        List<URL> urls = new ArrayList<>();
        try {
            for (ResourceRoot root : manifest.resourceRoots()) {
                if (Files.isDirectory(root.path())) {
                    urls.add(root.path().toUri().toURL());
                }
            }
            for (Path root : manifest.reloadableRoots()) {
                if (Files.isDirectory(root)) {
                    urls.add(root.toUri().toURL());
                }
            }
        } catch (MalformedURLException e) {
            LOG.debug("Cannot list the resources to configure Logback from", e);
            return;
        }
        try (URLClassLoader resources = new URLClassLoader(urls.toArray(new URL[0]), parent)) {
            Logback.configure(resources, true, false);
        } catch (IOException e) {
            LOG.debug("Cannot close the loader Logback was configured from", e);
        }
    }

    /**
     * Configures Logback from a generation's loader before the generation starts: the first, and one after a change of
     * a Logback configuration file among the class output.
     *
     * @param generation The generation's loader
     */
    void beforeGeneration(ClassLoader generation) {
        if (stale) {
            stale = false;
            configure(generation, changed);
            changed = false;
        }
    }

    /**
     * Configures Logback again, from the current generation's loader, which reads the resource roots live, when a
     * Logback configuration file among them changed.
     *
     * @param resources The settled resource changes of a batch
     * @param current   The current generation's loader
     */
    void resourcesChanged(Map<ResourceKind, SourceChanges> resources, ClassLoader current) {
        for (SourceChanges changes : resources.values()) {
            if (anyConfigurationFile(changes.changed()) || anyConfigurationFile(changes.deleted())) {
                configure(current, true);
                return;
            }
        }
    }

    /**
     * Marks Logback for configuring again before the next generation when a Logback configuration file of the class
     * output changed, which only a new generation sees.
     *
     * @param changeSet The changes of the class output
     */
    void outputChanged(ChangeSet changeSet) {
        if (anyConfigurationName(changeSet.changedResources()) || anyConfigurationName(changeSet.removedResources())) {
            changed = true;
            stale = true;
        }
    }

    private static void configure(ClassLoader resources, boolean changed) {
        if (isLogback()) {
            Logback.configure(resources, false, changed);
        }
    }

    private static boolean anyConfigurationFile(Collection<Path> files) {
        for (Path file : files) {
            Path name = file.getFileName();
            if (name != null && isConfigurationName(name.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyConfigurationName(Set<String> resources) {
        for (String resource : resources) {
            if (isConfigurationName(resource.substring(resource.lastIndexOf('/') + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigurationName(String name) {
        if (name.equals(AUTOCONFIG_FILE) || name.equals(TEST_AUTOCONFIG_FILE)) {
            return true;
        }
        String property = System.getProperty(CONFIG_FILE_PROPERTY);
        return property != null && (property.equals(name) || property.endsWith("/" + name) || property.endsWith("\\" + name));
    }

    private static boolean isLogback() {
        return ClassUtils.isPresent(LOGGER_CONTEXT, DevLogging.class.getClassLoader());
    }

    /**
     * What touches Logback's classes, loaded only once Logback is known to be present.
     */
    private static final class Logback {

        private static final String NONE = "";
        // the configuration file Logback was configured from last, JVM-wide as Logback is
        private static volatile @Nullable String configuredFrom;

        private Logback() {
        }

        static void configure(ClassLoader resources, boolean launcher, boolean changed) {
            try {
                if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
                    URL found = LogbackUtils.findConfiguration(resources);
                    String file = found == null ? NONE : found.toExternalForm();
                    String current = configuredFrom;
                    if (current == null) {
                        // what Logback's own startup found, through the loader that loaded it
                        URL own = LogbackUtils.findConfiguration(LoggerContext.class.getClassLoader());
                        current = own == null ? NONE : own.toExternalForm();
                    }
                    // the configuration Logback has is kept when it is the one found, and the appenders added to it with it
                    if (changed || !current.equals(file)) {
                        LogbackUtils.reconfigure(context, resources);
                    }
                    configuredFrom = file;
                    ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
                    if (launcher && found == null && root.getLevel() == Level.DEBUG) {
                        // Logback's basic configuration logs at DEBUG: without a configuration of the application's,
                        // the launcher and the application log at INFO
                        root.setLevel(Level.INFO);
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("Logback could not be configured from the application's resources: {}", e.getMessage(), e);
            }
        }
    }
}
