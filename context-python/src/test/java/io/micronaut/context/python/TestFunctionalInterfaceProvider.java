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
package io.micronaut.context.python;

import java.util.List;

/**
 * A provider like the ones the compiler generates, registered in {@code META-INF/services}.
 */
public final class TestFunctionalInterfaceProvider implements PythonFunctionalInterfaceProvider {

    @Override
    public List<Entry> entries() {
        return List.of(
            new Entry(PythonCallablesTest.Overloads.OtherCallback.class.getName(), 1, true),
            // an interface of a compile-time only dependency: skipped, the host access still builds
            new Entry("com.example.absent.Callback", 1, true)
        );
    }
}
