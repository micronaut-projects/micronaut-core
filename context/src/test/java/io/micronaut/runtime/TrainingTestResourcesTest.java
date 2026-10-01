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
package io.micronaut.runtime;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.PropertyExpressionResolver;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.runtime.TrainingRunTest.ChildJvm;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.testresources.client.FakeTestResourcesClient;
import io.micronaut.testresources.embedded.FakeEmbeddedTestResources;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a training run, in either mode, disables Micronaut Test Resources, which would
 * otherwise supply the properties that the configuration lacks and start a container for each one
 * that is read. Core has no dependency on Test Resources: {@link FakeTestResourcesClient} stands for
 * its client, in its package, and reads the switch of the client as the client does.
 */
class TrainingTestResourcesTest {
    static final String SPEC_NAME = "TrainingTestResourcesTest";
    private static final String CHILD_JVM = "child-jvm";
    private static final String CLIENT_ENABLED = "micronaut.test.resources.enabled";
    private static final String DISABLED = "Training run (" + ApplicationConfiguration.TRAINING_ENABLED + "=true): Micronaut Test Resources is on the class path, so this run set "
        + CLIENT_ENABLED + "=false, the switch of its client, while the configuration was read";

    @TempDir
    Path temp;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private @Nullable String clientSwitch;

    @BeforeEach
    void reset() {
        FakeTestResourcesClient.reset();
        ReadsTheProperty.URL.set(null);
        clientSwitch = System.getProperty(CLIENT_ENABLED);
        System.clearProperty(CLIENT_ENABLED);
        logs.start();
        micronautLogger().addAppender(logs);
    }

    @AfterEach
    void restore() {
        micronautLogger().detachAppender(logs);
        logs.stop();
        if (clientSwitch != null) {
            System.setProperty(CLIENT_ENABLED, clientSwitch);
        }
    }

    private static ch.qos.logback.classic.Logger micronautLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Micronaut.class);
    }

    private List<String> messages(Level level) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    @Test
    void testResourcesSuppliesTheMissingPropertyOutsideATrainingRun() throws IOException {
        // What the training run prevents: the property is supplied, and resolved as soon as a bean reads it
        try (ApplicationContext context = Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME))
            .start()) {

            assertTrue(context.isRunning());
            assertEquals("true", FakeTestResourcesClient.SWITCH.get());
            assertEquals(FakeTestResourcesClient.VALUE, ReadsTheProperty.URL.get());
            assertTrue(FakeTestResourcesClient.RESOLVED.get() > 0);
            assertTrue(context.containsBean(ComparesTheProperty.class));
        }
    }

    @Test
    void aLoadTrainingRunDisablesTestResources() throws IOException {
        ApplicationContext context = Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, "true", ApplicationConfiguration.TRAINING_MODE, "load"))
            .start();

        assertFalse(context.isRunning());
        // The client read its switch as false, so it supplied nothing, and the condition of ComparesTheProperty,
        // which compares the value of the property, resolved nothing
        assertEquals("false", FakeTestResourcesClient.SWITCH.get());
        assertEquals(0, FakeTestResourcesClient.RESOLVED.get());
        assertTrue(messages(Level.INFO).contains(DISABLED), () -> messages(Level.INFO).toString());
        // Only while the environment started
        assertNull(System.getProperty(CLIENT_ENABLED));
    }

    @Test
    void aStartTrainingRunDisablesTestResources() throws IOException {
        // No EmbeddedApplication: the training run leaves the context running, so its configuration can be checked
        try (ApplicationContext context = Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, "true"))
            .start()) {

            assertTrue(context.isRunning());
            assertEquals("false", FakeTestResourcesClient.SWITCH.get());
            // The bean that stands for a datasource got the configuration of the application, which lacks the URL
            assertEquals("none", ReadsTheProperty.URL.get());
            assertFalse(context.getProperty(FakeTestResourcesClient.PROPERTY, String.class).isPresent());
            assertFalse(context.containsBean(ComparesTheProperty.class));
            assertEquals(0, FakeTestResourcesClient.RESOLVED.get());
            assertTrue(messages(Level.INFO).contains(DISABLED), () -> messages(Level.INFO).toString());
            assertNull(System.getProperty(CLIENT_ENABLED));
        }
    }

    @Test
    void aSwitchThatIsOnlySetInTheConfigurationFailsATrainingRunWithTestResources() throws IOException {
        Path config = Files.createDirectories(temp.resolve("config"));
        Files.writeString(config.resolve("application.properties"),
            ApplicationConfiguration.TRAINING_ENABLED + "=true\n" + ApplicationConfiguration.TRAINING_MODE + "=load\n");

        ApplicationStartupException failure = assertThrows(ApplicationStartupException.class, () -> Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME))
            .overrideConfigLocations("file:" + config)
            .start());

        ConfigurationException cause = assertInstanceOf(ConfigurationException.class, failure.getCause());
        assertEquals("Micronaut Test Resources (" + FakeTestResourcesClient.Loader.class.getName() + ") has read the configuration of this training run, which must not resolve properties with it. "
            + "micronaut.application.training.enabled is only set in the configuration of the application, which is read together with Test Resources. "
            + "Set it as a system property, as the MICRONAUT_APPLICATION_TRAINING_ENABLED environment variable or as an argument of the application, "
            + "so that the training run disables Test Resources before it reads the configuration", cause.getMessage());
        // Test Resources was not disabled, but the run failed before anything read the property
        assertEquals("true", FakeTestResourcesClient.SWITCH.get());
        assertEquals(0, FakeTestResourcesClient.RESOLVED.get());

        // Without Test Resources, the same configuration is a training run that completes
        ApplicationContext context = Micronaut.build(new String[0])
            .properties(Map.of("spec.name", SPEC_NAME))
            .overrideConfigLocations("file:" + config)
            .start();
        assertFalse(context.isRunning());
        assertTrue(messages(Level.WARN).stream().anyMatch(message -> message.contains("bean definitions loaded")), () -> messages(Level.WARN).toString());
    }

    @Test
    void aTestResourcesModuleThatIgnoresTheSwitchFailsTheTrainingRun() throws IOException {
        ApplicationStartupException failure = assertThrows(ApplicationStartupException.class, () -> Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeEmbeddedTestResources.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, "true", ApplicationConfiguration.TRAINING_MODE, "load"))
            .start());

        ConfigurationException cause = assertInstanceOf(ConfigurationException.class, failure.getCause());
        assertEquals("Micronaut Test Resources (" + FakeEmbeddedTestResources.Loader.class.getName() + ") is on the class path of this training run and cannot be disabled: "
            + "the training run disables the Test Resources client with micronaut.test.resources.enabled=false, which this module does not read. "
            + "Remove it from the class path of the training run", cause.getMessage());
        assertEquals(0, FakeTestResourcesClient.RESOLVED.get());
        assertNull(System.getProperty(CLIENT_ENABLED));
    }

    @Test
    void theSwitchIsReadBeforeTheStartWithThePrecedenceOfTheStartedEnvironment() throws IOException {
        // The properties of the builder take precedence over the system properties: this is not a training run,
        // so Test Resources stays on
        System.setProperty(ApplicationConfiguration.TRAINING_ENABLED, "true");
        try (ApplicationContext context = Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, "false"))
            .start()) {

            assertTrue(context.isRunning());
            assertEquals("true", FakeTestResourcesClient.SWITCH.get());
            assertEquals(FakeTestResourcesClient.VALUE, ReadsTheProperty.URL.get());
            assertTrue(messages(Level.WARN).isEmpty(), () -> messages(Level.WARN).toString());
        } finally {
            System.clearProperty(ApplicationConfiguration.TRAINING_ENABLED);
        }

        // An argument of the application is read before the start too
        FakeTestResourcesClient.reset();
        ApplicationContext context = Micronaut.build("--" + ApplicationConfiguration.TRAINING_ENABLED + "=true", "--" + ApplicationConfiguration.TRAINING_MODE + "=load")
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME))
            .start();
        assertFalse(context.isRunning());
        assertEquals("false", FakeTestResourcesClient.SWITCH.get());
    }

    @Test
    void aBuilderSourceThatAConfigurationFileOverridesIsNotReadBeforeTheStart() throws IOException {
        // The configuration file (order -400) overrides the property source of the builder (order -1000), so this is
        // not a training run, and Test Resources stays on
        Path config = Files.createDirectories(temp.resolve("config"));
        Files.writeString(config.resolve("application.properties"), ApplicationConfiguration.TRAINING_ENABLED + "=false\n");
        try (ApplicationContext context = Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME))
            .propertySources(PropertySource.of("low", Map.of(ApplicationConfiguration.TRAINING_ENABLED, "true"), -1000))
            .overrideConfigLocations("file:" + config)
            .start()) {

            assertTrue(context.isRunning());
            assertEquals("true", FakeTestResourcesClient.SWITCH.get());
            assertEquals(FakeTestResourcesClient.VALUE, ReadsTheProperty.URL.get());
            assertTrue(messages(Level.WARN).isEmpty(), () -> messages(Level.WARN).toString());
        }
    }

    @Test
    void aSwitchThatTheStartedConfigurationTurnsOffIsNotATrainingRunAndSaysThatTestResourcesWasDisabled() throws IOException {
        // A source that the environment only reads when it starts, such as distributed configuration, overrides the builder
        try (ApplicationContext context = Micronaut.build(new String[0])
            .classLoader(classLoaderWith(FakeTestResourcesClient.Loader.class))
            .properties(Map.of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, "true"))
            .propertySourcesLocator(environment -> List.of(PropertySource.of("located", Map.of(ApplicationConfiguration.TRAINING_ENABLED, "false"), 100)))
            .start()) {

            assertTrue(context.isRunning());
            assertEquals("false", FakeTestResourcesClient.SWITCH.get());
            assertEquals("none", ReadsTheProperty.URL.get());
            assertEquals(List.of(ApplicationConfiguration.TRAINING_ENABLED + " was true before the configuration was read, and the configuration turns it off, so this run is not a training run. "
                + "Micronaut Test Resources was disabled all the same (" + CLIENT_ENABLED + "=false while the configuration was read). Set "
                + ApplicationConfiguration.TRAINING_ENABLED + " in one place only"), messages(Level.WARN));
            assertNull(System.getProperty(CLIENT_ENABLED));
        }
    }

    @Test
    @Tag(CHILD_JVM)
    void aTrainingRunThatASystemPropertyOrAnEnvironmentVariableTurnsOnDisablesTestResources() throws IOException {
        String classPath = servicesFor(FakeTestResourcesClient.Loader.class) + File.pathSeparator + System.getProperty("java.class.path");
        ChildJvm property = ChildJvm.run(Main.class, classPath, Map.of(),
            "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true", "-D" + ApplicationConfiguration.TRAINING_MODE + "=load");
        ChildJvm variable = ChildJvm.run(Main.class, classPath,
            Map.of("MICRONAUT_APPLICATION_TRAINING_ENABLED", "true", "MICRONAUT_APPLICATION_TRAINING_MODE", "load"));

        for (ChildJvm child : List.of(property, variable)) {
            assertEquals(0, child.exitCode(), child::output);
            assertTrue(child.output().contains(FakeTestResourcesClient.COMPUTED + "false, supplies []"), child::output);
            assertFalse(child.output().contains(FakeTestResourcesClient.COMPUTED + "true"), child::output);
            assertFalse(child.output().contains(FakeTestResourcesClient.RESOLVED_MESSAGE), child::output);
            assertTrue(child.output().contains(DISABLED), child::output);
        }

        // An environment variable that the builder excludes is not read before the start either: not a training run
        ChildJvm excluded = ChildJvm.run(MainWithoutTheVariable.class, classPath, Map.of("MICRONAUT_APPLICATION_TRAINING_ENABLED", "true"));
        assertEquals(0, excluded.exitCode(), excluded::output);
        assertTrue(excluded.output().contains(FakeTestResourcesClient.COMPUTED + "true, supplies [" + FakeTestResourcesClient.PROPERTY + "]"), excluded::output);
        assertTrue(excluded.output().contains(FakeTestResourcesClient.RESOLVED_MESSAGE), excluded::output);
        assertFalse(excluded.output().contains("Training run"), excluded::output);
        assertFalse(excluded.output().contains("was true before the configuration was read"), excluded::output);
    }

    /**
     * @param loader The property source loader of Test Resources to register
     * @return A class loader that also sees the loader and the resolver of the fake as services
     */
    private ClassLoader classLoaderWith(Class<? extends PropertySourceLoader> loader) throws IOException {
        URL services = Path.of(servicesFor(loader)).toUri().toURL();
        return new URLClassLoader(new URL[] {services}, TrainingTestResourcesTest.class.getClassLoader());
    }

    /**
     * @param loader The property source loader of Test Resources to register
     * @return A directory with the service files that register the loader and the resolver of the fake
     */
    private String servicesFor(Class<? extends PropertySourceLoader> loader) throws IOException {
        Path services = Files.createDirectories(temp.resolve("services").resolve("META-INF").resolve("services"));
        Files.writeString(services.resolve(PropertySourceLoader.class.getName()), loader.getName() + "\n");
        Files.writeString(services.resolve(PropertyExpressionResolver.class.getName()), FakeTestResourcesClient.Resolver.class.getName() + "\n");
        return temp.resolve("services").toString();
    }

    /**
     * A bean whose condition compares the value of the property that Test Resources supplies, which
     * resolves it.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = FakeTestResourcesClient.PROPERTY, pattern = "jdbc:.*")
    static final class ComparesTheProperty {
    }

    /**
     * Stands for a datasource: an eager bean that reads the URL when it is created.
     */
    @Context
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class ReadsTheProperty {
        static final AtomicReference<@Nullable String> URL = new AtomicReference<>();

        ReadsTheProperty(@Value("${" + FakeTestResourcesClient.PROPERTY + ":none}") String url) {
            URL.set(url);
        }
    }

    /**
     * The application run by the child JVM, without an EmbeddedApplication.
     */
    static final class Main {
        public static void main(String[] args) {
            Micronaut.build(args)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME))
                .start();
        }
    }

    /**
     * The application run by the child JVM, with the environment variable of the switch excluded.
     */
    static final class MainWithoutTheVariable {
        public static void main(String[] args) {
            Micronaut.build(args)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME))
                .environmentVariableExcludes("MICRONAUT_APPLICATION_TRAINING_ENABLED")
                .start()
                .close();
        }
    }
}
