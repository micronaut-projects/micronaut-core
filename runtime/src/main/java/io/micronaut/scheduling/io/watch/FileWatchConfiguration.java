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
package io.micronaut.scheduling.io.watch;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.Toggleable;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the file watch service.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@ConfigurationProperties(FileWatchConfiguration.PREFIX)
@Requires(property = FileWatchConfiguration.PATHS)
public class FileWatchConfiguration implements Toggleable {

    /**
     * The prefix to use to configure the watch service.
     */
    public static final String PREFIX = "micronaut.io.watch";

    /**
     * The watch paths.
     */
    public static final String PATHS = PREFIX + ".paths";

    /**
     * Setting to enable and disable server watch.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * Setting to enable and disable restart.
     */
    public static final String RESTART = PREFIX + ".restart";

    private boolean enabled = true;
    private boolean restart = false;
    private List<Path> paths = Collections.singletonList(Paths.get("src/main"));
    private Duration checkInterval = Duration.ofMillis(300);

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Should the server be shutdown when a watch event fires. Note that if set to true an external process
     * like {@code gradle run --continuous} or Kubernetes replication controller is required to restart the container.
     *
     * @return Is restart enabled. Defaults to false.
     */
    public boolean isRestart() {
        return restart;
    }

    /**
     * Set whether restart is enabled.
     *
     * @param restart True if restart is to be enabled
     * @see #isRestart()
     */
    public void setRestart(boolean restart) {
        this.restart = restart;
    }

    /**
     * Whether watch is enabled.
     * @param enabled True if is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The paths to watch.
     * @return A lists of {@link Path} objects
     */
    public List<Path> getPaths() {
        return paths;
    }

    /**
     * Sets the watch paths to use.
     * @param paths The watch paths
     */
    public void setPaths(@NonNull List<Path> paths) {
        ArgumentUtils.requireNonNull("paths", paths);
        this.paths = paths;
    }

    /**
     * The interval to wait between checks.
     * @return The interval to wait.
     */
    public @NonNull Duration getCheckInterval() {
        return checkInterval;
    }

    /**
     * Sets the interval to wait between file watch polls.
     *
     * @param checkInterval The check interval
     */
    public void setCheckInterval(@NonNull Duration checkInterval) {
        ArgumentUtils.requireNonNull("checkInterval", checkInterval);
        this.checkInterval = checkInterval;
    }
}
