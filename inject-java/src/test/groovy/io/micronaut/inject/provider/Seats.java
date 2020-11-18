/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.provider;

import edu.umd.cs.findbugs.annotations.Nullable;
import org.atinject.tck.auto.Drivers;
import org.atinject.tck.auto.Seat;
import org.atinject.tck.auto.Tire;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Seats {
    Provider<Seat> driversSeatProvider;
    Provider<Tire> spareTireProvider;

    Seats(@Drivers Provider<Seat> driversSeatProvider,
          @Named("spare") Provider<Tire> spareTireProvider,
          @Nullable Provider<NotABean> notABeanProvider) {
        this.driversSeatProvider = driversSeatProvider;
        this.spareTireProvider = spareTireProvider;
        assert notABeanProvider == null;
    }
}
