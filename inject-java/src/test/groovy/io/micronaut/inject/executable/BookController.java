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
package io.micronaut.inject.executable;

import io.micronaut.context.annotation.Executable;

import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Inject;
import java.util.List;

@Executable
public class BookController {
    @Inject
    BookService bookService;

    public String show(Long id) {
        return String.format("%d - The Stand", id);
    }

    public String showArray(Long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    public String showPrimitive(long id) {
        return String.format("%d - The Stand", id);
    }

    public String showPrimitiveArray(long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    public void showVoidReturn(List<String> jobNames) {
        jobNames.add("test");
    }

    public int showPrimitiveReturn(int[] values) {
        return values[0];
    }

    @Executable
    public static String showStatic(Long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    public static String showArrayStatic(Long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    @Executable
    public static String showPrimitiveStatic(long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    public static String showPrimitiveArrayStatic(long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    @Executable
    public static void showVoidReturnStatic(List<String> jobNames) {
        jobNames.add("test");
    }

    @Executable
    public static int showPrimitiveReturnStatic(int[] values) {
        return values[0];
    }

    String showPackageProtected(Long id) {
        return String.format("%d - The Stand", id);
    }

    protected String showProtected(Long id) {
        return String.format("%d - The Stand", id);
    }

    private String showPrivate(Long id) {
        return String.format("%d - The Stand", id);
    }

    @ReflectiveAccess
    protected String showProtectedReflectiveAccess(Long id) {
        return String.format("%d - The Stand", id);
    }

    @ReflectiveAccess
    private String showPrivateReflectiveAccess(Long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    static String showPackageProtectedStatic(Long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    static protected String showProtectedStatic(Long id) {
        return String.format("%d - The Stand", id);
    }

    static private String showPrivateStatic(Long id) {
        return String.format("%d - The Stand", id);
    }

    @ReflectiveAccess
    @Executable
    static protected String showProtectedReflectiveAccessStatic(Long id) {
        return String.format("%d - The Stand", id);
    }

    @ReflectiveAccess
    @Executable
    static private String showPrivateReflectiveAccessStatic(Long id) {
        return String.format("%d - The Stand", id);
    }

}
