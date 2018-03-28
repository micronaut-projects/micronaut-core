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

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.TaskScheduler;

import javax.inject.Singleton;
import java.time.Duration;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class Application implements ApplicationEventListener<ServerStartupEvent> {
    private final TaskScheduler taskScheduler;
    private final OffersRepository offersRepository;


    public Application(
            TaskScheduler taskScheduler,
            OffersRepository offersRepository) {
        this.taskScheduler = taskScheduler;
        this.offersRepository = offersRepository;
    }

    public static void main(String... args) {
        Micronaut.run(Application.class);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        // this is not really representative of a real system where data would probably exist,
        // but we need this delay as pets are created on startup in pets service
        taskScheduler.schedule(Duration.ofSeconds(20), offersRepository::createInitialOffers);
    }
}
