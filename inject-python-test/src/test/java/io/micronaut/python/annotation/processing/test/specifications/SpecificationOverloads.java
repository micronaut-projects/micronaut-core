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
package io.micronaut.python.annotation.processing.test.specifications;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * A Java bean shaped like a data repository: its methods are overloaded on {@code Iterable} /
 * {@code Collection} / {@code Map} and custom functional interfaces, on two custom functional
 * interfaces of different arities, and on {@code Iterable} and {@code Publisher} (an interface
 * with a single abstract method that is not annotated), and are called from Python with lambdas.
 */
public class SpecificationOverloads {

    private final List<String> people = new ArrayList<>(List.of("Denis", "Josh"));

    public List<String> people() {
        return people;
    }

    public int deleteAll(Iterable<String> names) {
        int deleted = 0;
        for (String name : names) {
            if (people.remove(name)) {
                deleted++;
            }
        }
        return deleted;
    }

    public int deleteAll(PredicateSpec<String> spec) {
        int deleted = 0;
        for (String name : List.copyOf(people)) {
            if (spec.test(name, people.size()) && people.remove(name)) {
                deleted++;
            }
        }
        return deleted;
    }

    public int updateAll(Collection<String> names) {
        return names.size();
    }

    public int updateAll(UpdateSpec<String> spec) {
        int updated = 0;
        for (int i = 0; i < people.size(); i++) {
            String replacement = spec.update(people.get(i), i, people.size());
            if (replacement != null) {
                people.set(i, replacement);
                updated++;
            }
        }
        return updated;
    }

    public String describe(Map<String, String> attributes) {
        return "map:" + attributes.size();
    }

    public String describe(PredicateSpec<String> spec) {
        return "spec:" + spec.test("x", 1);
    }

    public Optional<String> findOne(PredicateSpec<String> spec) {
        return people.stream().filter(name -> spec.test(name, people.size())).findFirst();
    }

    public Optional<String> findOne(QuerySpec<String> spec) {
        return people.stream().filter(name -> spec.test(name, "query", people.size())).findFirst();
    }

    public String subscribe(Iterable<String> names) {
        return "iterable:" + StreamSupport.stream(names.spliterator(), false).count();
    }

    public String subscribe(Publisher<String> publisher) {
        List<String> received = new ArrayList<>();
        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                received.add("subscribed");
            }

            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                received.add("error");
            }

            @Override
            public void onComplete() {
                received.add("complete");
            }
        });
        return "publisher:" + String.join(",", received);
    }

    public long count(PredicateSpec<String> spec) {
        return people.stream().filter(name -> spec.test(name, people.size())).count();
    }
}
