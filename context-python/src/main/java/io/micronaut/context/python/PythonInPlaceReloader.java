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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Patches edited Python modules into the running application, for development mode.
 *
 * <p>A Python edit that changed only bodies leaves the generated Java classes byte for byte the same,
 * and changes the module's source, and the bytecode the compiler wrote for it, under
 * {@value GraalPyContextFactory#APPLICATION_SRC_PATH}. This reloader takes such a change: every GraalPy
 * context of the application, the primary one, the pooled ones and those of the asyncio event loops,
 * runs {@code __micronaut_hot_patch} of the runtime module, which executes the new code and merges it
 * into the module, class and function objects already there. The generated classes call into Python by
 * name and the runtime's caches hold those objects, so they all run the new code, and nothing restarts.</p>
 *
 * <p>Refused, so that the application restarts: package initialisers ({@code __init__.py}) and the main
 * modules ({@code __main__.py}, {@code main.py}), which run once at startup; the modules the compiler
 * generates ({@code __micronaut_*}); any other resource; and any change that adds or removes a file.
 * A merge refuses what it cannot follow (see {@code micronaut_runtime.py}), and a failure in any
 * context fails the whole reload: the restart that follows discards what the other contexts applied.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
@Singleton
@Requires(condition = DevelopmentMode.Active.class)
final class PythonInPlaceReloader implements InPlaceResourceReloader {

    private static final Logger LOG = LoggerFactory.getLogger(PythonInPlaceReloader.class);
    private static final String HOT_PATCH = "__micronaut_hot_patch";
    private static final String PYCACHE = "__pycache__";
    private static final Duration CONTEXT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PASSES = 4;

    private final BeanProvider<PythonApplicationRuntime> runtime;

    PythonInPlaceReloader(BeanProvider<PythonApplicationRuntime> runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean canReload(Set<String> changedResources, Set<String> removedResources) {
        if (!removedResources.isEmpty() || changedResources.isEmpty()) {
            return false;
        }
        for (String resource : changedResources) {
            if (moduleFile(resource) == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Result reload(Set<String> changedResources) throws Exception {
        Set<String> modules = new LinkedHashSet<>();
        List<String> files = new ArrayList<>();
        for (String resource : changedResources) {
            String file = moduleFile(resource);
            if (file == null) {
                throw new IllegalArgumentException("Not a Python module the runtime patches in place: " + resource);
            }
            files.add(file);
            modules.add(sourceOf(file));
        }
        PythonApplicationRuntime application = runtime.get();
        String[] paths = files.toArray(String[]::new);
        long deadline = System.nanoTime() + CONTEXT_TIMEOUT.toNanos();
        Set<Context> done = new HashSet<>();
        Set<String> imported = new LinkedHashSet<>();
        PythonPool pool = application.pool();
        // a context created while the files changed may have read the old ones: once no context is being created,
        // the contexts registered since the last pass are patched too, until a pass finds none
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (pool != null && !pool.awaitContextCreation(deadline)) {
                throw new IllegalStateException("A Python context was still being created after " + CONTEXT_TIMEOUT);
            }
            Map<String, CompletableFuture<List<String>>> patches = new LinkedHashMap<>();
            if (done.add(application.context())) {
                patches.put("the primary context", patchOnNewThread(application, application.context(), paths));
            }
            if (pool != null) {
                for (Context pooled : pool.pooledContextsSnapshot()) {
                    if (done.add(pooled)) {
                        patches.put("pooled context " + done.size(), patchOnNewThread(application, pooled, paths));
                    }
                }
                for (Map.Entry<PythonEventLoop, Context> entry : pool.eventLoopContextsSnapshot().entrySet()) {
                    Context context = entry.getValue();
                    if (done.add(context)) {
                        // on its loop, between two callbacks: the loop's tasks never see a module half patched
                        CompletableFuture<List<String>> future = new CompletableFuture<>();
                        entry.getKey().execute(() -> complete(future, application, context, paths));
                        patches.put("event-loop context " + done.size(), future);
                    }
                }
            }
            if (patches.isEmpty()) {
                break;
            }
            for (Map.Entry<String, CompletableFuture<List<String>>> entry : patches.entrySet()) {
                try {
                    imported.addAll(entry.getValue().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new IllegalStateException("Python refused the patch in " + entry.getKey() + ": " + cause.getMessage(), cause);
                } catch (TimeoutException e) {
                    throw new IllegalStateException("Python did not patch " + entry.getKey() + " within " + CONTEXT_TIMEOUT, e);
                }
            }
        }
        LOG.debug("Patched the Python module(s) {} in {} context(s); imported: {}", modules, done.size(), imported);
        return new Result(modules.size(), "Python module(s)");
    }

    private static CompletableFuture<List<String>> patchOnNewThread(PythonApplicationRuntime application, Context context, String[] paths) {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        // a thread per context: each context has its own interpreter lock, and one busy context must not hold up the
        // others, nor, past the timeout, the development runtime
        Thread thread = new Thread(() -> complete(future, application, context, paths), "micronaut-dev-python-patch");
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    private static void complete(CompletableFuture<List<String>> future, PythonApplicationRuntime application, Context context, String[] paths) {
        try {
            future.complete(application.withContextClassLoader(() -> PythonContextRegistry.withTrackedExecutionFrame(context, () -> patch(context, paths))));
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    private static List<String> patch(Context context, String[] paths) {
        Value result = PythonContextRuntime.helper(context, HOT_PATCH).execute((Object) paths);
        Value failure = result.getArrayElement(1);
        if (!failure.isNull()) {
            throw new IllegalStateException(failure.asString());
        }
        Value names = result.getArrayElement(0);
        List<String> patched = new ArrayList<>((int) names.getArraySize());
        for (long i = 0; i < names.getArraySize(); i++) {
            patched.add(names.getArrayElement(i).asString());
        }
        return patched;
    }

    /**
     * The path of a module file under the application's source root, for a resource this reloader patches.
     *
     * @param resource The resource name
     * @return The path relative to the source root, or null when the resource is not one it patches
     */
    static @Nullable String moduleFile(String resource) {
        if (!resource.startsWith(GraalPyContextFactory.APPLICATION_SRC_PATH)) {
            return null;
        }
        String file = resource.substring(GraalPyContextFactory.APPLICATION_SRC_PATH.length());
        String[] segments = file.split("/");
        String name = segments[segments.length - 1];
        boolean bytecode = name.endsWith(".pyc");
        if (!bytecode && !name.endsWith(".py")) {
            return null;
        }
        if (bytecode && (segments.length < 2 || !segments[segments.length - 2].equals(PYCACHE))) {
            return null;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals("..") || segment.startsWith("__micronaut")) {
                return null;
            }
        }
        // the module name: hello.py, or hello.graalpy253-313.pyc
        String module = name.substring(0, name.indexOf('.') < 0 ? name.length() : name.indexOf('.'));
        if (module.isEmpty() || module.equals("__init__") || module.equals("__main__")) {
            return null;
        }
        int depth = bytecode ? segments.length - 1 : segments.length;
        if (depth == 1 && (module + ".py").equals(GraalPyContextFactory.APPLICATION_MAIN)) {
            // the application's main script, run once as __main__ when the context starts
            return null;
        }
        return file;
    }

    /**
     * The source file a module file stands for: itself, or the source of a bytecode file.
     */
    private static String sourceOf(String file) {
        if (!file.endsWith(".pyc")) {
            return file;
        }
        int slash = file.lastIndexOf('/');
        String directory = file.substring(0, slash);
        String name = file.substring(slash + 1);
        String parent = directory.substring(0, Math.max(0, directory.length() - PYCACHE.length()));
        return parent + name.substring(0, name.indexOf('.')) + ".py";
    }
}
