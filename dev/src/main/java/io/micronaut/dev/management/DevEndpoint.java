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
package io.micronaut.dev.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /dev} reports the generation, the strategy, the retained beans, the watched roots and
 * the last compilation failure; {@code POST /dev/reload} compiles everything and restarts if
 * anything changed, the manual trigger for an IDE the watcher did not hear from.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Endpoint(id = DevEndpoint.NAME, defaultSensitive = false)
@Requires(classes = Endpoint.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
public class DevEndpoint {

    /**
     * The endpoint's name.
     */
    public static final String NAME = "dev";

    static final String KEY_GENERATION = "generation";
    static final String KEY_STRATEGY = "strategy";
    static final String KEY_RELOADING = "reloading";
    static final String KEY_RETAINED = "retainedBeans";
    static final String KEY_WATCHED = "watchedRoots";
    static final String KEY_LIVERELOAD = "liveReload";
    static final String KEY_FAILURE = "lastFailure";
    static final String KEY_ACCEPTED = "accepted";
    static final String KEY_ACTION = "action";
    static final String ACTION_RELOAD = "reload";

    private final DevRuntime runtime;

    /**
     * @param runtime The runtime
     */
    public DevEndpoint(DevRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * @return The state of development mode
     */
    @Read
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put(KEY_GENERATION, runtime.generation());
        status.put(KEY_STRATEGY, runtime.strategy().name());
        status.put(KEY_RELOADING, runtime.isReloading());
        status.put(KEY_RETAINED, runtime.retainedCount());
        status.put(KEY_WATCHED, runtime.watchedRoots().stream().map(Object::toString).toList());
        status.put(KEY_LIVERELOAD, runtime.liveReload().map(server -> Map.of("port", server.port(), "connections", server.connections())).orElse(null));
        status.put(KEY_FAILURE, runtime.lastFailure().map(DevEndpoint::describe).orElse(null));
        return status;
    }

    /**
     * {@code POST /dev/reload} requests a reload; it runs after this response is sent, since a restart
     * stops the server answering it.
     *
     * @param action The action, {@code reload} being the only one
     * @return An acknowledgement, {@code accepted} false for an unknown action
     */
    @Write
    public Map<String, Object> act(@Selector String action) {
        if (!ACTION_RELOAD.equals(action)) {
            return Map.of(KEY_ACCEPTED, false, KEY_ACTION, action);
        }
        runtime.requestReload();
        return Map.of(KEY_ACCEPTED, true, KEY_ACTION, action);
    }

    private static Map<String, Object> describe(CompileFailure failure) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("kind", failure.kind().name().toLowerCase(java.util.Locale.ROOT));
        description.put("at", failure.at().toString());
        description.put("errors", failure.errors().stream().map(d -> (d.file() != null ? d.file() + ":" + d.line() + ": " : "") + d.message()).toList());
        description.put("warnings", failure.diagnostics().stream().filter(d -> d.severity() == CompileDiagnostic.Severity.WARNING).count());
        return description;
    }
}
