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
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.runtime.TrainingRunTest.ChildJvm;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the {@code load} mode of a training run ({@link ApplicationConfiguration#TRAINING_MODE}):
 * it loads the bean definitions, creates no bean and does not start the application, so it completes
 * for an application whose beans cannot be created where the training runs.
 */
class TrainingLoadTest {
    static final String SPEC_NAME = "TrainingLoadTest";
    // Tests that start a child JVM with the test class path: a constrained run can exclude this tag
    private static final String CHILD_JVM = "child-jvm";
    private static final String SERVER = "training-load-test.server";
    private static final String EAGER_CLIENT = "training-load-test.eager-client";
    private static final String POST_CONSTRUCT_CLIENT = "training-load-test.post-construct-client";
    private static final String OPTIONAL = "training-load-test.optional";
    private static final String LOOKUP = "training-load-test.lookup";
    private static final String METADATA = "training-load-test.metadata";
    private static final String NOT_RUNNING = "java.lang.IllegalStateException: Cannot resolve beans until the context is running";
    private static final String LOAD_LOG = "Training run (" + ApplicationConfiguration.TRAINING_ENABLED + "=true, " + ApplicationConfiguration.TRAINING_MODE + "=load)";
    private static final String ANNOUNCEMENT = LOAD_LOG + ": this JVM is a training run and does not serve traffic. "
        + "It loads the enabled bean definitions and the classes they name, creates no bean and does not start the application";
    private static final String DEPLOYMENT_WARNING = ". Never set this property or MICRONAUT_APPLICATION_TRAINING_ENABLED on a deployment target";
    private static final String FAREWELL = "This JVM was a training run and served no traffic";
    private static final String WARMUP_IGNORED = "Training run (" + ApplicationConfiguration.TRAINING_MODE + "=load): the micronaut.application.training.warmup settings are ignored, "
        + "because this mode starts no server and sends no warm-up request";
    private static final Pattern SUMMARY = Pattern.compile("loaded (\\d+) of (\\d+) bean definitions and the (\\d+) types they name in \\d+ms, skipped (\\d+) that could not be evaluated or loaded");
    private static final String ENABLED = "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true";
    private static final String LOAD = "-D" + ApplicationConfiguration.TRAINING_MODE + "=load";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void reset() {
        TestApplication.CREATED.set(0);
        TestApplication.STARTED.set(0);
        TestApplication.STOPPED.set(0);
        EagerClient.CREATED.set(0);
        PostConstructClient.CREATED.set(0);
        LookedUp.CREATED.set(0);
        StartupListener.STARTUPS.set(0);
        logs.start();
        micronautLogger().addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        micronautLogger().detachAppender(logs);
        logs.stop();
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

    private static Map<String, Object> loadMode(Object... more) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spec.name", SPEC_NAME);
        properties.put(ApplicationConfiguration.TRAINING_ENABLED, "true");
        properties.put(ApplicationConfiguration.TRAINING_MODE, "load");
        for (int i = 0; i < more.length; i += 2) {
            properties.put((String) more[i], more[i + 1]);
        }
        return properties;
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void loadsTheBeanDefinitionsWithoutCreatingABeanOrStartingTheApplication() {
        // A server application with two beans that cannot be created here: a start would fail, or wait for a shutdown
        ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(loadMode(EAGER_CLIENT, "true", POST_CONSTRUCT_CLIENT, "true"))
            .start();

        assertFalse(context.isRunning());
        // A context that was never started is not running either: closing it is what stops its environment
        assertFalse(context.getEnvironment().isRunning());
        assertEquals(0, TestApplication.CREATED.get());
        assertEquals(0, TestApplication.STARTED.get());
        assertEquals(0, TestApplication.STOPPED.get());
        assertEquals(0, EagerClient.CREATED.get());
        assertEquals(0, PostConstructClient.CREATED.get());
        assertEquals(0, StartupListener.STARTUPS.get());
        // One warning before the definitions are loaded, one on the way out, and neither announces an exit here
        assertEquals(List.of(
            ANNOUNCEMENT + DEPLOYMENT_WARNING,
            LOAD_LOG + ": bean definitions loaded, closing the context. " + FAREWELL), messages(Level.WARN));
        Summary summary = Summary.of(String.join("\n", messages(Level.INFO)));
        assertTrue(summary.loaded() > 0 && summary.loaded() <= summary.references(), summary::toString);
        assertTrue(summary.types() > summary.loaded(), summary::toString);
        assertEquals(0, summary.skipped());
    }

    @Test
    void loadsTheDefinitionsThatAStartedContextHas() {
        // "logger.levels" enables a bean that requires the Environment bean, which a context registers when it starts
        Map<String, Object> properties = Map.of("spec.name", SPEC_NAME, SERVER, "false", "logger.levels.training-load-test", "INFO");
        List<String> started;
        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST).properties(properties).start()) {
            started = names(context.getAllBeanDefinitions());
        }
        assertTrue(started.stream().anyMatch(name -> name.contains("PropertiesLoggingLevelsConfigurer")), started::toString);
        assertTrue(started.contains(TestApplication.class.getName()), started::toString);

        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST).properties(properties).build()) {
            context.getEnvironment().start();
            TrainingLoad.Result result = TrainingLoad.load(context);

            assertFalse(context.isRunning());
            assertEquals(started, names(context.getAllBeanDefinitions()));
            assertEquals(started.size(), result.loaded());
            assertEquals(0, result.skipped());
            assertEquals(context.getBeanDefinitionReferences().size(), result.references());
        }
        assertEquals(0, TestApplication.CREATED.get());
        assertEquals(1, StartupListener.STARTUPS.get(), "only the started context publishes the startup event");
    }

    @Test
    void modeIsReadInAnyCase() {
        for (String mode : List.of("LOAD", "Load", " load ")) {
            ApplicationContext context = Micronaut.build(new String[0])
                .environments(Environment.TEST)
                .properties(loadMode(ApplicationConfiguration.TRAINING_MODE, mode))
                .start();

            assertFalse(context.isRunning(), mode);
        }
        assertEquals(0, TestApplication.CREATED.get());
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void startModeAndABlankModeStartTheApplication() {
        for (String mode : List.of("start", "START", "")) {
            TestApplication.STARTED.set(0);
            ApplicationContext context = Micronaut.build(new String[0])
                .environments(Environment.TEST)
                .properties(loadMode(ApplicationConfiguration.TRAINING_MODE, mode))
                .start();

            assertFalse(context.isRunning(), mode);
            assertEquals(1, TestApplication.STARTED.get(), mode);
        }
    }

    @Test
    void modeIsNotReadWhenTheSwitchIsOff() {
        // Not a server application, so start() returns. Neither "load" nor a value that is no mode has any effect
        for (String mode : List.of("load", "no-such-mode")) {
            TestApplication.STARTED.set(0);
            TestApplication.STOPPED.set(0);
            try (ApplicationContext context = Micronaut.build(new String[0])
                .environments(Environment.TEST)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME, SERVER, "false", ApplicationConfiguration.TRAINING_MODE, mode))
                .start()) {

                assertTrue(context.isRunning(), mode);
                assertEquals(1, TestApplication.STARTED.get(), mode);
                assertEquals(0, TestApplication.STOPPED.get(), mode);
            }
        }
        assertEquals(List.of(), messages(Level.WARN));
    }

    @Test
    void unknownModeFailsTheTrainingRun() {
        Micronaut micronaut = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(loadMode(ApplicationConfiguration.TRAINING_MODE, "laod"));

        ApplicationStartupException e = assertThrows(ApplicationStartupException.class, micronaut::start);

        assertTrue(e.getMessage().contains("Unknown training mode [laod]: " + ApplicationConfiguration.TRAINING_MODE + " must be start or load"), e::getMessage);
        assertEquals(0, TestApplication.CREATED.get());
    }

    @Test
    void warmupSettingsAreIgnoredWithAWarning() {
        // repeat=0 fails a training run that starts the application: here the warm-up settings are not even bound
        ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(loadMode("micronaut.application.training.warmup.paths", "/hello", "micronaut.application.training.warmup.repeat", "0"))
            .start();

        assertFalse(context.isRunning());
        assertEquals(List.of(
            ANNOUNCEMENT + DEPLOYMENT_WARNING,
            WARMUP_IGNORED,
            LOAD_LOG + ": bean definitions loaded, closing the context. " + FAREWELL), messages(Level.WARN));
    }

    @Test
    void endsAnApplicationWithoutAnEmbeddedApplication() {
        // Without "spec.name" there is no EmbeddedApplication: a training run that starts the application cannot stop it
        ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(Map.<String, Object>of(ApplicationConfiguration.TRAINING_ENABLED, "true", ApplicationConfiguration.TRAINING_MODE, "load"))
            .start();

        assertFalse(context.isRunning());
        assertEquals(List.of(
            ANNOUNCEMENT + DEPLOYMENT_WARNING,
            LOAD_LOG + ": bean definitions loaded, closing the context. " + FAREWELL), messages(Level.WARN));
    }

    @Test
    void skipsADefinitionWhoseMetadataCannotBeRead() {
        Map<String, Object> properties = Map.of("spec.name", SPEC_NAME);
        TrainingLoad.Result complete;
        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST).properties(properties).build()) {
            context.getEnvironment().start();
            complete = TrainingLoad.load(context);
        }
        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST).properties(properties)
            .beanDefinitions(new UnreadableDefinition()).build()) {
            context.getEnvironment().start();
            TrainingLoad.Result result = TrainingLoad.load(context);

            assertEquals(complete.references() + 1, result.references());
            assertEquals(complete.loaded(), result.loaded());
            assertEquals(complete.skipped() + 1, result.skipped());
        }
    }

    @Test
    void skipsADefinitionWhoseConditionLooksUpABean() {
        Map<String, Object> properties = Map.of("spec.name", SPEC_NAME, SERVER, "false", LOOKUP, "true");
        List<String> started;
        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST).properties(properties).start()) {
            started = names(context.getAllBeanDefinitions());
        }
        // A started context evaluates the two conditions, and the lookup creates the bean
        assertTrue(started.contains(EnabledByALookup.class.getName()), started::toString);
        assertTrue(started.contains(EnabledByABeanContextLookup.class.getName()), started::toString);
        assertEquals(1, LookedUp.CREATED.get());

        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST).properties(properties).build()) {
            context.getEnvironment().start();
            TrainingLoad.Result result = TrainingLoad.load(context);

            // A context that is not running refuses the lookups: no bean is created and the two definitions are left out
            assertEquals(2, result.skipped());
            assertEquals(started.size() - 2, result.loaded());
            assertFalse(result.types().contains(EnabledByALookup.class));
            assertFalse(result.types().contains(EnabledByABeanContextLookup.class));
            assertTrue(result.types().contains(LookedUp.class));
        }
        assertEquals(1, LookedUp.CREATED.get(), "only the started context created the bean");
        List<String> skipped = messages(Level.INFO).stream().filter(message -> message.contains("skipped bean definition")).toList();
        assertEquals(2, skipped.size(), skipped::toString);
        for (Class<?> bean : List.of(EnabledByALookup.class, EnabledByABeanContextLookup.class)) {
            assertTrue(skipped.stream().anyMatch(message -> message.contains("$" + bean.getSimpleName() + "$") && message.endsWith(": " + NOT_RUNNING)), skipped::toString);
        }
    }

    @Test
    void readsTheTypesThatACompiledDefinitionNames() {
        List<Class<?>> named = List.of(NamesTypes.class, ConstructorType.class, FieldType.class, MethodType.class,
            ExecutableParent.class, ReturnType.class, ArgumentType.class, TypeParameter.class);

        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST)
            .properties(Map.<String, Object>of("spec.name", SPEC_NAME)).build()) {
            context.getEnvironment().start();
            Set<Class<?>> types = TrainingLoad.load(context).types();

            // Nothing else names these types
            assertEquals(List.of(), named.stream().filter(types::contains).toList());
        }
        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST)
            .properties(Map.<String, Object>of("spec.name", SPEC_NAME, METADATA, "true")).build()) {
            context.getEnvironment().start();
            TrainingLoad.Result result = TrainingLoad.load(context);

            // The constructor, the injected field and method, and the executable method with its declaring type and its type parameter
            assertEquals(List.of(), named.stream().filter(type -> !result.types().contains(type)).toList());
            assertEquals(0, result.skipped());
        }
    }

    @Test
    void readsTheTypesThatAHandWrittenDefinitionNames() {
        List<Class<?>> named = List.of(HandWrittenDefinition.BeanType.class, ExposedType.class, DeclaringType.class,
            InterceptedType.class, TargetType.class, TargetDefinitionType.class);

        try (ApplicationContext context = ApplicationContext.builder().environments(Environment.TEST)
            .properties(Map.<String, Object>of("spec.name", SPEC_NAME)).beanDefinitions(new HandWrittenDefinition()).build()) {
            context.getEnvironment().start();
            TrainingLoad.Result result = TrainingLoad.load(context);

            // The exposed types, the declaring type, and the types of an AOP proxy and of its target
            assertEquals(List.of(), named.stream().filter(type -> !result.types().contains(type)).toList());
            assertEquals(0, result.skipped());
        }
    }

    @Test
    void aFailureInsideTheLoadFailsTheTrainingRunAndClosesTheContext() {
        StartsTheContextEarly micronaut = new StartsTheContextEarly();
        micronaut.environments(Environment.TEST).properties(loadMode());

        ApplicationStartupException e = assertThrows(ApplicationStartupException.class, micronaut::start);

        assertInstanceOf(ConfigurationException.class, e.getCause());
        assertTrue(e.getMessage().contains(StartsTheContextEarly.FAILURE_MESSAGE), e::getMessage);
        // The context was running when the load failed, so it was closed on the way out
        assertEquals(1, micronaut.contexts.size());
        assertFalse(micronaut.contexts.get(0).isRunning());
        assertFalse(micronaut.contexts.get(0).getEnvironment().isRunning());
        // The run is announced, and nothing says that it ended well
        assertEquals(List.of(ANNOUNCEMENT + DEPLOYMENT_WARNING), messages(Level.WARN));
        assertEquals(List.of(), messages(Level.INFO).stream().filter(message -> message.contains("loaded")).toList());
    }

    @Test
    @Tag(CHILD_JVM)
    void aFailureInsideTheLoadExitsWithNonZero() {
        ChildJvm child = ChildJvm.run(FailingMain.class, System.getProperty("java.class.path"), Map.of(), ENABLED, LOAD);

        assertEquals(1, child.exitCode(), child::output);
        assertTrue(child.output().contains(ANNOUNCEMENT + ", then exits with status 0" + DEPLOYMENT_WARNING), child::output);
        assertTrue(child.output().contains("Error starting Micronaut server: " + StartsTheContextEarly.FAILURE_MESSAGE), child::output);
        assertFalse(child.output().contains(FAREWELL), child::output);
    }

    @Test
    @Tag(CHILD_JVM)
    void completesWhereAnEagerBeanCannotBeCreated() {
        ChildJvm loaded = ChildJvm.run(Main.class, System.getProperty("java.class.path"), Map.of(), ENABLED, LOAD, "-D" + EAGER_CLIENT + "=true");

        assertEquals(0, loaded.exitCode(), loaded::output);
        assertTrue(loaded.output().contains(ANNOUNCEMENT + ", then exits with status 0" + DEPLOYMENT_WARNING), loaded::output);
        assertTrue(loaded.output().contains(LOAD_LOG + ": bean definitions loaded, closing the context and exiting with status 0. " + FAREWELL), loaded::output);
        assertEquals(0, Summary.of(loaded.output()).skipped(), loaded::output);
        // No bean class was initialized, let alone instantiated, and the application was not started
        assertFalse(loaded.output().contains(EagerClient.INITIALIZED_MESSAGE), loaded::output);
        assertFalse(loaded.output().contains(TestApplication.INITIALIZED_MESSAGE), loaded::output);
        assertFalse(loaded.output().contains(TestApplication.STARTED_MESSAGE), loaded::output);

        // The same application in a training run that starts it: the context cannot start
        ChildJvm started = ChildJvm.run(Main.class, System.getProperty("java.class.path"), Map.of(), ENABLED, "-D" + EAGER_CLIENT + "=true");

        assertEquals(1, started.exitCode(), started::output);
        assertTrue(started.output().contains(EagerClient.INITIALIZED_MESSAGE), started::output);
        assertTrue(started.output().contains(EagerClient.class.getSimpleName() + DatabaseConnection.FAILURE_MESSAGE), started::output);
        assertFalse(started.output().contains(TestApplication.STARTED_MESSAGE), started::output);
    }

    @Test
    @Tag(CHILD_JVM)
    void completesWhereABeanOfTheApplicationCannotBeInitialized() {
        // The mode and the switch come from the environment, as a build would pass them, and not in the canonical case
        ChildJvm loaded = ChildJvm.run(Main.class, System.getProperty("java.class.path"),
            Map.of("MICRONAUT_APPLICATION_TRAINING_ENABLED", "true", "MICRONAUT_APPLICATION_TRAINING_MODE", "LOAD"), "-D" + POST_CONSTRUCT_CLIENT + "=true");

        assertEquals(0, loaded.exitCode(), loaded::output);
        assertTrue(loaded.output().contains(FAREWELL), loaded::output);
        assertFalse(loaded.output().contains(PostConstructClient.INITIALIZED_MESSAGE), loaded::output);
        assertFalse(loaded.output().contains(TestApplication.INITIALIZED_MESSAGE), loaded::output);

        // In a training run that starts the application, the bean injected into the application fails in @PostConstruct
        ChildJvm started = ChildJvm.run(Main.class, System.getProperty("java.class.path"),
            Map.of("MICRONAUT_APPLICATION_TRAINING_ENABLED", "true"), "-D" + POST_CONSTRUCT_CLIENT + "=true");

        assertEquals(1, started.exitCode(), started::output);
        assertTrue(started.output().contains(PostConstructClient.class.getSimpleName() + DatabaseConnection.FAILURE_MESSAGE), started::output);
        assertFalse(started.output().contains(TestApplication.STARTED_MESSAGE), started::output);
    }

    @Test
    @Tag(CHILD_JVM)
    void skipsADefinitionThatNamesAnAbsentClass(@TempDir Path temp) throws Exception {
        ChildJvm complete = ChildJvm.run(Main.class, System.getProperty("java.class.path"), Map.of(), ENABLED, LOAD, "-D" + OPTIONAL + "=true");
        // The same class path without the class of a constructor argument of UsesOptionalDependency
        ChildJvm incomplete = ChildJvm.run(Main.class, classPathWithout(OptionalDependency.class, temp), Map.of(), ENABLED, LOAD, "-D" + OPTIONAL + "=true");

        assertEquals(0, complete.exitCode(), complete::output);
        assertEquals(0, incomplete.exitCode(), incomplete::output);
        Summary all = Summary.of(complete.output());
        Summary some = Summary.of(incomplete.output());
        assertEquals(0, all.skipped(), complete::output);
        assertEquals(1, some.skipped(), incomplete::output);
        assertEquals(all.references(), some.references());
        assertEquals(all.loaded() - 1, some.loaded());
        // The skipped definition is named, with the cause
        assertTrue(incomplete.output().lines().anyMatch(line -> line.contains("skipped bean definition")
            && line.contains(UsesOptionalDependency.class.getSimpleName()) && line.contains(OptionalDependency.class.getSimpleName())), incomplete::output);
        assertFalse(complete.output().contains("skipped bean definition"), complete::output);
    }

    private static List<String> names(Collection<BeanDefinition<Object>> definitions) {
        return definitions.stream().map(BeanDefinition::getName).sorted().toList();
    }

    /**
     * Copies the directory of the test classes without one class and returns the class path of
     * this JVM with that copy in place of the directory.
     */
    private static String classPathWithout(Class<?> absent, Path temp) throws Exception {
        Path root = Path.of(absent.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assumptions.assumeTrue(Files.isDirectory(root), "The test classes are not in a directory: " + root);
        Path classFile = root.resolve(absent.getName().replace('.', '/') + ".class");
        assertTrue(Files.exists(classFile), classFile::toString);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.toList()) {
                Path target = temp.resolve(root.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else if (!file.equals(classFile)) {
                    Files.copy(file, target);
                }
            }
        }
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().equals(root.toAbsolutePath()) ? temp.toString() : entry)
            .collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * The INFO line of a {@code load} training run.
     */
    private record Summary(int loaded, int references, int types, int skipped) {
        static Summary of(String output) {
            Matcher matcher = SUMMARY.matcher(output);
            assertTrue(matcher.find(), output);
            return new Summary(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
        }
    }

    /**
     * What a bean does when it needs a service that is absent where the training runs: it opens a
     * connection to a port nothing listens on.
     */
    static final class DatabaseConnection {
        static final String FAILURE_MESSAGE = ": no database to connect to";

        private DatabaseConnection() {
        }

        static void open(Class<?> client) {
            try {
                int closedPort;
                try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                    closedPort = listener.getLocalPort();
                }
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), closedPort), 5_000);
                }
            } catch (IOException e) {
                throw new IllegalStateException(client.getSimpleName() + FAILURE_MESSAGE, e);
            }
            throw new IllegalStateException(client.getSimpleName() + FAILURE_MESSAGE + ", but something accepted the connection");
        }
    }

    /**
     * An eagerly initialized bean that connects in its constructor.
     */
    @Context
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = EAGER_CLIENT, value = "true")
    static final class EagerClient {
        static final String INITIALIZED_MESSAGE = "EagerClient class initialized";
        static final AtomicInteger CREATED = new AtomicInteger();

        static {
            System.out.println(INITIALIZED_MESSAGE);
        }

        EagerClient() {
            CREATED.incrementAndGet();
            DatabaseConnection.open(EagerClient.class);
        }
    }

    /**
     * A bean that connects once it is constructed. The application injects it, so it is created
     * when the application is.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = POST_CONSTRUCT_CLIENT, value = "true")
    static final class PostConstructClient {
        static final String INITIALIZED_MESSAGE = "PostConstructClient class initialized";
        static final AtomicInteger CREATED = new AtomicInteger();

        static {
            System.out.println(INITIALIZED_MESSAGE);
        }

        PostConstructClient() {
            CREATED.incrementAndGet();
        }

        @PostConstruct
        void connect() {
            DatabaseConnection.open(PostConstructClient.class);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class StartupListener implements ApplicationEventListener<StartupEvent> {
        static final AtomicInteger STARTUPS = new AtomicInteger();

        @Override
        public void onApplicationEvent(StartupEvent event) {
            STARTUPS.incrementAndGet();
        }
    }

    /**
     * A class that is absent from the class path of one child JVM.
     */
    static final class OptionalDependency {
    }

    /**
     * A bean whose constructor names {@link OptionalDependency}, which is not a bean: nothing may
     * create it.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = OPTIONAL, value = "true")
    static final class UsesOptionalDependency {
        UsesOptionalDependency(OptionalDependency dependency) {
        }
    }

    /**
     * A bean that a condition looks up.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = LOOKUP, value = "true")
    static final class LookedUp {
        static final AtomicInteger CREATED = new AtomicInteger();

        LookedUp() {
            CREATED.incrementAndGet();
        }
    }

    /**
     * A condition that needs a bean, which only a context that is running resolves.
     */
    static final class LooksUpABean implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getBean(LookedUp.class) != null;
        }
    }

    /**
     * The same lookup through the bean context of the condition.
     */
    static final class LooksUpABeanInTheBeanContext implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getBeanContext().getBean(LookedUp.class) != null;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = LOOKUP, value = "true")
    @Requires(condition = LooksUpABean.class)
    static final class EnabledByALookup {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = LOOKUP, value = "true")
    @Requires(condition = LooksUpABeanInTheBeanContext.class)
    static final class EnabledByABeanContextLookup {
    }

    /**
     * The types that only {@link NamesTypes} names. None is a bean: nothing may create it.
     */
    static final class ConstructorType {
    }

    static final class FieldType {
    }

    static final class MethodType {
    }

    static final class ReturnType {
    }

    static final class ArgumentType<T> {
    }

    static final class TypeParameter {
    }

    /**
     * Declares the executable method of {@link NamesTypes}, which does not expose this type.
     */
    abstract static class ExecutableParent {
        @Executable
        ReturnType execute(ArgumentType<TypeParameter> argument) {
            return new ReturnType();
        }
    }

    /**
     * A compiled definition with every kind of injection point and an executable method.
     */
    @Singleton
    @Bean(typed = NamesTypes.class)
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = METADATA, value = "true")
    static class NamesTypes extends ExecutableParent {
        @Inject
        FieldType field;

        NamesTypes(ConstructorType constructor) {
        }

        @Inject
        void method(MethodType method) {
        }
    }

    /**
     * The types that only {@link HandWrittenDefinition} names.
     */
    static final class ExposedType {
    }

    static final class DeclaringType {
    }

    static final class InterceptedType {
    }

    static final class TargetType {
    }

    static final class TargetDefinitionType {
    }

    /**
     * A definition registered by hand, as a module may do: nothing has loaded the classes it names
     * by the time it is read. Each accessor names a type that no other does.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final class HandWrittenDefinition implements RuntimeBeanDefinition<HandWrittenDefinition.BeanType>,
        ProxyBeanDefinition<HandWrittenDefinition.BeanType>, AdvisedBeanType<HandWrittenDefinition.BeanType> {

        @Override
        public Class<BeanType> getBeanType() {
            return BeanType.class;
        }

        @Override
        public Set<Class<?>> getExposedTypes() {
            return Set.of(ExposedType.class);
        }

        @Override
        public Optional<Class<?>> getDeclaringType() {
            return Optional.of(DeclaringType.class);
        }

        @Override
        public Class<? super BeanType> getInterceptedType() {
            return (Class) InterceptedType.class;
        }

        @Override
        public Class<BeanType> getTargetType() {
            return (Class) TargetType.class;
        }

        @Override
        public Class<BeanDefinition<BeanType>> getTargetDefinitionType() {
            return (Class) TargetDefinitionType.class;
        }

        @Override
        public BeanType instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
            throw new IllegalStateException("Nothing may create this bean");
        }

        static final class BeanType {
        }
    }

    /**
     * A definition registered by hand whose metadata fails the way a missing class does.
     */
    static final class UnreadableDefinition implements RuntimeBeanDefinition<Runnable> {
        @Override
        public Class<Runnable> getBeanType() {
            return Runnable.class;
        }

        @Override
        public ConstructorInjectionPoint<Runnable> getConstructor() {
            throw new NoClassDefFoundError("training/load/test/Absent");
        }

        @Override
        public Runnable instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
            throw new IllegalStateException("Nothing may create this bean");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class TestApplication implements EmbeddedApplication<TestApplication> {
        static final String INITIALIZED_MESSAGE = "TestApplication class initialized";
        static final String STARTED_MESSAGE = "TestApplication started";
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger STARTED = new AtomicInteger();
        static final AtomicInteger STOPPED = new AtomicInteger();

        static {
            System.out.println(INITIALIZED_MESSAGE);
        }

        private final ApplicationContext applicationContext;
        private final ApplicationConfiguration applicationConfiguration;
        private final boolean server;
        private volatile boolean running;

        TestApplication(ApplicationContext applicationContext,
                        ApplicationConfiguration applicationConfiguration,
                        @Nullable PostConstructClient client,
                        @Value("${" + SERVER + ":true}") boolean server) {
            this.applicationContext = applicationContext;
            this.applicationConfiguration = applicationConfiguration;
            this.server = server;
            CREATED.incrementAndGet();
        }

        @Override
        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }

        @Override
        public ApplicationConfiguration getApplicationConfiguration() {
            return applicationConfiguration;
        }

        @Override
        public boolean isServer() {
            return server;
        }

        @Override
        public boolean isShutdownHookNeeded() {
            return false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public TestApplication start() {
            running = true;
            STARTED.incrementAndGet();
            System.out.println(STARTED_MESSAGE);
            return this;
        }

        @Override
        public TestApplication stop() {
            running = false;
            STOPPED.incrementAndGet();
            return this;
        }
    }

    /**
     * Hands the training run a context that is already running, which {@code configure()} rejects:
     * a failure inside the load, after the run was announced.
     */
    static final class StartsTheContextEarly extends Micronaut {
        static final String FAILURE_MESSAGE = "Bean context is already running. The configure() method can only be called prior to startup";
        final List<ApplicationContext> contexts = new ArrayList<>();

        @Override
        protected ApplicationContext newApplicationContext() {
            ApplicationContext context = super.newApplicationContext().start();
            contexts.add(context);
            return context;
        }
    }

    /**
     * The application run by the child JVM whose training run fails inside the load.
     */
    static final class FailingMain {
        public static void main(String[] args) {
            new StartsTheContextEarly().args(args).start();
        }
    }

    /**
     * The application run by the child JVM: a server application, so a training run that starts it
     * and does not stop it would make the child time out.
     */
    static final class Main {
        public static void main(String[] args) {
            Micronaut.build(args)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME))
                .start();
        }
    }
}
