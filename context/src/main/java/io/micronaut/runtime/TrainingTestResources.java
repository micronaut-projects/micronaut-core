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
 *     that the factory of the Test Resources client reads: the client then neither contacts a server
 *     nor supplies a property. The previous value is restored once the environment has started, after
 *     the property source of the client has computed its properties.</li>
 *     <li>Once the environment has started, {@link #checkDisabled(Environment, boolean)} fails the
 *     training run if Test Resources is on the class path and was not disabled: the switch was only
 *     set in the configuration of the application, which the environment reads together with the
 *     property source of Test Resources, or the module does not read {@value #CLIENT_ENABLED}.</li>
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
     * client a no-op that contacts no server and supplies no property.
     */
    static final String CLIENT_ENABLED = "micronaut.test.resources.enabled";

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
     * Restores {@value #CLIENT_ENABLED} once the environment has started. The property source of the
     * client has computed its properties by then, none, and the resolver of the client only resolves
     * the placeholders of that property source.
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
            LOG.info("Training run ({}=true): Micronaut Test Resources is on the class path and disabled ({}=false while the configuration was read), so it supplies no property",
                ApplicationConfiguration.TRAINING_ENABLED, CLIENT_ENABLED);
        }
    }
}
