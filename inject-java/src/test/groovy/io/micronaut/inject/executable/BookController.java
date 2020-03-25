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
package io.micronaut.inject.executable;

import io.micronaut.context.annotation.Executable;

import javax.inject.Inject;
import java.util.List;

@Executable
public class BookController {
    @Inject
    BookService bookService;

    @Executable
    public String show(Long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    public String showArray(Long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    @Executable
    public String showPrimitive(long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    public String showPrimitiveArray(long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    @Executable
    public void showVoidReturn(List<String> jobNames) {
        jobNames.add("test");
    }

    @Executable
    public int showPrimitiveReturn(int[] values) {
        return values[0];
    }
}