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
package io.micronaut.benchmark.metadata;

import io.micronaut.core.annotation.Introspected;

/** Distinct generated property metadata avoids a single constant benchmark input. */
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class LookupFixture {
    @LookupRule(name = "member0", count = 10)
    public String property0;
    @LookupRule(name = "member1", count = 11)
    public String property1;
    @LookupRule(name = "member2", count = 12)
    public String property2;
    @LookupRule(name = "member3", count = 13)
    public String property3;
    @LookupRule(name = "member4", count = 14)
    public String property4;
    @LookupRule(name = "member5", count = 15)
    public String property5;
    @LookupRule(name = "member6", count = 16)
    public String property6;
    @LookupRule(name = "member7", count = 17)
    public String property7;
    @LookupRule(name = "member8", count = 18)
    public String property8;
    @LookupRule(name = "member9", count = 19)
    public String property9;
    @LookupRule(name = "member10", count = 20)
    public String property10;
    @LookupRule(name = "member11", count = 21)
    public String property11;
    @LookupRule(name = "member12", count = 22)
    public String property12;
    @LookupRule(name = "member13", count = 23)
    public String property13;
    @LookupRule(name = "member14", count = 24)
    public String property14;
    @LookupRule(name = "member15", count = 25)
    public String property15;
}
