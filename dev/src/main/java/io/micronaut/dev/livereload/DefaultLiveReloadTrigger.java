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
package io.micronaut.dev.livereload;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevRuntime;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/**
 * The trigger bean of a development context: sends to the launcher's server, does nothing when
 * there is none.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
final class DefaultLiveReloadTrigger implements LiveReloadTrigger {

    private final DevRuntime runtime;

    DefaultLiveReloadTrigger(DevRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean isEnabled() {
        return runtime.liveReload().isPresent();
    }

    @Override
    public void reload() {
        runtime.liveReload().ifPresent(server -> server.reload("/", false));
    }

    @Override
    public void reload(Path path) {
        runtime.liveReload().ifPresent(server -> server.reload(runtime.publicPathOf(path), false));
    }

    @Override
    public void reloadCss(Path stylesheet) {
        runtime.liveReload().ifPresent(server -> server.reload(runtime.publicPathOf(stylesheet), true));
    }
}
