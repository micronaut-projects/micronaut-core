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
package io.micronaut.context.watch

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

class MetadataComparisonSpec extends Specification {

    private static final String POLL = "example.Poll"
    private static final String SCHEDULED = "io.micronaut.scheduling.annotation.Scheduled"
    private static final String SCHEDULES = "io.micronaut.scheduling.annotation.Schedules"

    void "a composed annotation whose stereotype's values change is not the same"() {
        expect: "the same direct annotation and stereotype names, a different fixedDelay"
        !MetadataComparison.same(polled("1s"), polled("2s"))

        and: "the same values compare as the same"
        MetadataComparison.same(polled("1s"), polled("1s"))
    }

    void "a composed annotation whose repeatable stereotype's values change is not the same"() {
        expect:
        !MetadataComparison.same(polledRepeatable("1s"), polledRepeatable("2s"))
        MetadataComparison.same(polledRepeatable("1s"), polledRepeatable("1s"))
    }

    void "a direct annotation whose values change is not the same"() {
        given:
        def before = new MutableAnnotationMetadata()
        before.addDeclaredAnnotation(POLL, [interval: "1s"])
        def after = new MutableAnnotationMetadata()
        after.addDeclaredAnnotation(POLL, [interval: "2s"])

        expect:
        !MetadataComparison.same(before, after)
    }

    // @Poll, meta-annotated with @Scheduled(fixedDelay = ...), as the compiler records it on a method
    private static MutableAnnotationMetadata polled(String fixedDelay) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(POLL, [:])
        metadata.addDeclaredStereotype([POLL], SCHEDULED, [fixedDelay: fixedDelay])
        metadata
    }

    private static MutableAnnotationMetadata polledRepeatable(String fixedDelay) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(POLL, [:])
        metadata.addDeclaredRepeatableStereotype([POLL], SCHEDULES, AnnotationValue.builder(SCHEDULED).member("fixedDelay", fixedDelay).build())
        metadata
    }
}
