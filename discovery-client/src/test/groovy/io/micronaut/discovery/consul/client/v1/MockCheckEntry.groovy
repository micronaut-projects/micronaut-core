/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.consul.client.v1
/**
 * @author graemerocher
 * @since 1.0
 */
class MockCheckEntry extends CheckEntry {
    MockCheckEntry(String id) {
        super(id)
        setStatus(Check.Status.PASSING.name().toLowerCase())
    }

    @Override
    void setNotes(String notes) {
        super.setNotes(notes)
    }

    @Override
    void setName(String name) {
        super.setName(name)
    }

    @Override
    void setStatus(String status) {
        super.setStatus(status)
    }
}
