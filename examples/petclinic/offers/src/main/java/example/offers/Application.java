/*
 * Copyright 2018 original authors
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
package example.offers;

import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.runtime.ParticleApplication;
import org.particleframework.runtime.server.event.ServerStartupEvent;
import org.particleframework.scheduling.executor.ScheduledExecutorServiceConfig;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class Application implements ApplicationEventListener<ServerStartupEvent> {
    private final ScheduledExecutorService executorService;
    private final OffersRepository offersRepository;


    public Application(
            @Named(ScheduledExecutorServiceConfig.NAME) ScheduledExecutorService executorService,
            OffersRepository offersRepository) {
        this.executorService = executorService;
        this.offersRepository = offersRepository;
    }

    public static void main(String... args) {
        ParticleApplication.run(Application.class);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        // this is not really representative of a real system where data would probably exist,
        // but we need this delay as pets are created on startup in pets service
        executorService.schedule(offersRepository::createInitialOffers, 20, TimeUnit.SECONDS);
    }
}
