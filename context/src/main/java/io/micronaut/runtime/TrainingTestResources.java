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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps Micronaut Test Resources out of a training run ({@link ApplicationConfiguration#TRAINING_ENABLED}),
 * in both modes.
 *
 * <p>Test Resources supplies the properties that the configuration of an application lacks, such as
 * {@code datasources.default.url}, with containers that it starts on demand. Its client registers a
 * {@link PropertySourceLoader} and a {@link io.micronaut.context.env.PropertyExpressionResolver}. When
 * the environment starts, the property source asks the Test Resources server which of the missing
 * properties it can supply, and adds them as placeholders. When something reads one of them, a bean or
 * a condition that compares its value, the resolver asks the server for the value, and the server starts
 * the container. The Micronaut build plugins put the client on the class path of the run tasks and of
 * the tests, not on the runtime class path that a JAR or a container image is built from. A training
 * run has to see the configuration that the application has, so it disables Test Resources:</p>
 *
 * <ul>
 *     <li>If the switch is on before the environment starts, {@link Micronaut#start()} sets the system
 *     property {@value #CLIENT_ENABLED} to {@code false} while the environment starts. It is the switch
 *     that the factory of the Test Resources client reads when it creates a client: the client then
 *     neither contacts a server nor supplies a property. The system property gets its previous value
 *     back once the environment has started, after the property source of the client has computed its
 *     properties. The environment keeps the value {@code false}, which it read with the other system
 *     properties; nothing in Test Resources reads it from there.</li>
 *     <li>Once the environment has started, {@link #checkDisabled(Environment, boolean)} fails the
 *     training run if Test Resources is on the class path and was not disabled: the switch was only
 *     set in the configuration of the application, which the environment reads together with the
 *     property source of Test Resources, or the module does not read {@value #CLIENT_ENABLED}.</li>
 *     <li>If the switch was on before the environment started but the started environment turns it
 *     off, the run is not a training run, and {@link #warnNotATrainingRun(Environment)} says that Test
 *     Resources was disabled all the same.</li>
 * </ul>
 *
 * <p>Two cases are not covered, and the check cannot see them. The started environment does not say
 * whether Test Resources supplied a property: the loader of Test Resources returns a property source
 * for the application and one for each active environment, all with the same name, and the
 * environment keeps only the last one, which has nothing left to add once the first one has added the
 * missing properties.</p>
 *
 * <ul>
 *     <li>The factory of the client returns a client that it has already created in this JVM from
 *     system properties before it reads {@value #CLIENT_ENABLED}. A training run that follows an
 *     application context with Test Resources in the same JVM, in practice in a test, still uses that
 *     client.</li>
 *     <li>The resolver of the client resolves any {@code ${auto.test.resources.*}} expression, with a
 *     client that it creates when it resolves the first one, after the system property has its
 *     previous value back. The property source of the disabled client adds no such placeholder, so
 *     only one written in the configuration of the application could reach a client.</li>
 * </ul>
 *
 * <p>Micronaut core has no dependency on Test Resources: it recognizes the property source loaders of
 * Test Resources by their package. {@link Micronaut#start()} only refers to this class once the
 * training run switch is on.</p>
 *
 * @since 5.3.0
 */
@Internal
final class TrainingTestResources {

    /**
     * The switch of the Micronaut Test Resources client, a system property: {@code false} makes the
     * client that the factory creates a no-op that contacts no server and supplies no property.
     */
    private static final String CLIENT_ENABLED = "micronaut.test.resources.enabled";

    // The messages of a training run come from one logger, whatever the mode
    private static final Logger LOG = LoggerFactory.getLogger(Micronaut.class);
    private static final String PACKAGE = "io.micronaut.testresources.";
    // The only module of Test Resources that reads CLIENT_ENABLED
    private static final String CLIENT_PACKAGE = PACKAGE + "client.";

    private TrainingTestResources() {
    }

    /**
     * Disables the Test Resources client for the environment that is about to start.
     *
     * @return The previous value of {@value #CLIENT_ENABLED}, for {@link #restoreClient(String)}
     */
    static @Nullable String disableClient() {
        String previous = System.getProperty(CLIENT_ENABLED);
        System.setProperty(CLIENT_ENABLED, Boolean.FALSE.toString());
        return previous;
    }

    /**
     * Gives the system property {@value #CLIENT_ENABLED} its previous value back once the environment
     * has started, after the property source of the client has computed its properties: none. The
     * started environment keeps the value {@code false}. The resolver of the client resolves any
     * {@code ${auto.test.resources.*}} expression, with a client that it creates from then on; the
     * property source added none, so only one written in the configuration could reach it.
     *
     * @param previous The value that {@link #disableClient()} returned
     */
    static void restoreClient(@Nullable String previous) {
        if (previous == null) {
            System.clearProperty(CLIENT_ENABLED);
        } else {
            System.setProperty(CLIENT_ENABLED, previous);
        }
    }

    /**
     * Fails the training run if a module of Test Resources took part in starting the environment.
     *
     * @param environment The started environment of a training run
     * @param clientDisabled Whether {@link #disableClient()} was in effect while the environment started
     * @throws ConfigurationException if Test Resources is on the class path and was not disabled
     */
    static void checkDisabled(Environment environment, boolean clientDisabled) {
        boolean client = false;
        for (PropertySourceLoader loader : environment.getPropertySourceLoaders()) {
            String type = loader.getClass().getName();
            if (!type.startsWith(PACKAGE)) {
                continue;
            }
            if (!clientDisabled) {
                throw new ConfigurationException("Micronaut Test Resources (" + type + ") has read the configuration of this training run, which must not resolve properties with it. "
                    + ApplicationConfiguration.TRAINING_ENABLED + " is only set in the configuration of the application, which is read together with Test Resources. "
                    + "Set it as a system property, as the " + Micronaut.TRAINING_ENABLED_ENVIRONMENT_VARIABLE + " environment variable or as an argument of the application, "
                    + "so that the training run disables Test Resources before it reads the configuration");
            }
            if (!type.startsWith(CLIENT_PACKAGE)) {
                throw new ConfigurationException("Micronaut Test Resources (" + type + ") is on the class path of this training run and cannot be disabled: "
                    + "the training run disables the Test Resources client with " + CLIENT_ENABLED + "=false, which this module does not read. "
                    + "Remove it from the class path of the training run");
            }
            client = true;
        }
        if (client && LOG.isInfoEnabled()) {
            // What the run did, not what the client did: a client that this JVM created before ignores the switch
            LOG.info("Training run ({}=true): Micronaut Test Resources is on the class path, so this run set {}=false, the switch of its client, while the configuration was read",
                ApplicationConfiguration.TRAINING_ENABLED, CLIENT_ENABLED);
        }
    }

    /**
     * Says that Test Resources was disabled for a run that is not a training run: the switch was on
     * before the environment started, and a source that the environment only reads when it starts
     * turned it off.
     *
     * @param environment The started environment of a run that is not a training run
     */
    static void warnNotATrainingRun(Environment environment) {
        if (!LOG.isWarnEnabled()) {
            return;
        }
        for (PropertySourceLoader loader : environment.getPropertySourceLoaders()) {
            if (loader.getClass().getName().startsWith(PACKAGE)) {
                LOG.warn("{} was true before the configuration was read, and the configuration turns it off, so this run is not a training run. "
                        + "Micronaut Test Resources was disabled all the same ({}=false while the configuration was read). Set {} in one place only",
                    ApplicationConfiguration.TRAINING_ENABLED, CLIENT_ENABLED, ApplicationConfiguration.TRAINING_ENABLED);
                return;
            }
        }
    }
}
