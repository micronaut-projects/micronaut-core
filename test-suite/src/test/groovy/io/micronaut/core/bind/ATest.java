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
package io.micronaut.core.bind;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * An interface representing an ATest.
 *
 * @author Cindy Turpin
 * @since 2.x
 */
public class ATest implements Comparable<ATest>, Serializable {


    @Override
    public int compareTo(@NotNull ATest o) {
        return 0;
    }

    @NotNull
    public String getName() {
        return null;
    }

    @NotNull
    public String getValue() {
        return null;
    }

}
