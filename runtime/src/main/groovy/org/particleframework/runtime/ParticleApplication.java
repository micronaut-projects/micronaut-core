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
package org.particleframework.runtime;

import org.particleframework.context.ApplicationContext;
import org.particleframework.context.DefaultApplicationContext;
import org.particleframework.context.env.Environment;
import org.particleframework.server.EmbeddedServer;

import java.util.Optional;

/**
 * <p>A Particle application </p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ParticleApplication {

    private ParticleApplication() {

    }

    public static ApplicationContext run(String...args) {
        return run(new Class[0], args);
    }

    public static ApplicationContext run(Class cls, String...args) {
        return run(new Class[]{cls}, args);
    }

    public static ApplicationContext run(Class[] classes, String...args) {
        // TODO: Add command line argument parsing / command line property source

        // TODO: Deduce default environment
        ApplicationContext applicationContext = new DefaultApplicationContext("test");

        // Add packages to scan
        Environment environment = applicationContext.getEnvironment();
        for(Class cls : classes) {
            environment.addPackage(cls.getPackage());
        }

        applicationContext.start();
        // TODO: error handling for start()
        Optional<EmbeddedServer> embeddedContainerBean = applicationContext.findBean(EmbeddedServer.class);

        embeddedContainerBean.ifPresent((embeddedServer -> {
            try {
                embeddedServer.start();
                // TODO: Different protocols and hosts
                System.out.println("Server Running: http://localhost:"  + embeddedServer.getPort());
            } catch (Exception e) {
                // TODO: handle failed server start
                e.printStackTrace();
            }
        }));
        return applicationContext;
    }

}
