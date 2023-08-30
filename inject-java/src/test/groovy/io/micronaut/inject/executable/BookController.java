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
        return "%d - The Stand".formatted(id);
    }

    public String showArray(Long[] id) {
        return "%d - The Stand".formatted(id[0]);
    }

    public String showPrimitive(long id) {
        return "%d - The Stand".formatted(id);
    }

    public String showPrimitiveArray(long[] id) {
        return "%d - The Stand".formatted(id[0]);
    }

    public void showVoidReturn(List<String> jobNames) {
        jobNames.add("test");
    }

    public int showPrimitiveReturn(int[] values) {
        return values[0];
    }

    @Executable
    public static String showStatic(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @Executable
    public static String showArrayStatic(Long[] id) {
        return "%d - The Stand".formatted(id[0]);
    }

    @Executable
    public static String showPrimitiveStatic(long id) {
        return "%d - The Stand".formatted(id);
    }

    @Executable
    public static String showPrimitiveArrayStatic(long[] id) {
        return "%d - The Stand".formatted(id[0]);
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
        return "%d - The Stand".formatted(id);
    }

    protected String showProtected(Long id) {
        return "%d - The Stand".formatted(id);
    }

    private String showPrivate(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @ReflectiveAccess
    protected String showProtectedReflectiveAccess(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @ReflectiveAccess
    private String showPrivateReflectiveAccess(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @Executable
    static String showPackageProtectedStatic(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @Executable
    static protected String showProtectedStatic(Long id) {
        return "%d - The Stand".formatted(id);
    }

    static private String showPrivateStatic(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @ReflectiveAccess
    @Executable
    static protected String showProtectedReflectiveAccessStatic(Long id) {
        return "%d - The Stand".formatted(id);
    }

    @ReflectiveAccess
    @Executable
    static private String showPrivateReflectiveAccessStatic(Long id) {
        return "%d - The Stand".formatted(id);
    }

}
