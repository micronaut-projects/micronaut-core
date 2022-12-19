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
package io.micronaut.http.server.tck;

import io.micronaut.http.server.tck.tests.BodyArgumentTest;
import io.micronaut.http.server.tck.tests.BodyTest;
import io.micronaut.http.server.tck.tests.ConsumesTest;
import io.micronaut.http.server.tck.tests.CookiesTest;
import io.micronaut.http.server.tck.tests.DeleteWithoutBodyTest;
import io.micronaut.http.server.tck.tests.ErrorHandlerTest;
import io.micronaut.http.server.tck.tests.FilterErrorTest;
import io.micronaut.http.server.tck.tests.FiltersTest;
import io.micronaut.http.server.tck.tests.FluxTest;
import io.micronaut.http.server.tck.tests.HelloWorldTest;
import io.micronaut.http.server.tck.tests.MiscTest;
import io.micronaut.http.server.tck.tests.ParameterTest;
import io.micronaut.http.server.tck.tests.RemoteAddressTest;
import io.micronaut.http.server.tck.tests.ResponseStatusTest;
import io.micronaut.http.server.tck.tests.StatusTest;
import io.micronaut.http.server.tck.tests.VersionTest;

/**
 * Http Server TCK Test Suite.
 * @author Sergio del Amo
 * @since 3.8.0
 */
public interface HttpServerTestSuite extends
    BodyArgumentTest,
    ConsumesTest,
    ParameterTest,
    StatusTest,
    ResponseStatusTest,
    VersionTest,
    FluxTest,
    CookiesTest,
    FiltersTest,
    MiscTest,
    DeleteWithoutBodyTest,
    RemoteAddressTest,
    ErrorHandlerTest,
    FilterErrorTest,
    BodyTest,
    HelloWorldTest {
}
