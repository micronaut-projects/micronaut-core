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
package io.micronaut.jdt.beans;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * A record whose components are deliberately not in alphabetical order, so that a compiler that
 * reports the accessors alphabetically would produce a different introspection.
 *
 * @param zulu the zulu value
 * @param yankee the yankee value
 * @param alpha the alpha value
 * @param mike the mike values
 */
@Introspected
public record OrderedRecord(String zulu, int yankee, boolean alpha, List<String> mike) {
}
