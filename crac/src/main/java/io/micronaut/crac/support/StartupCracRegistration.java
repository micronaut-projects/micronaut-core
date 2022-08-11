/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.crac.support;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers all defined Resources for Coordinated Restore at Checkpoint at application startup.
 */
@Singleton
public class StartupCracRegistration implements ApplicationEventListener<ApplicationStartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(StartupCracRegistration.class);

    private final CracResourceRegistrar registrar;

    public StartupCracRegistration(CracResourceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Startup detected. Registering CRaC resources");
        }
        registrar.registerResources();
    }
}
