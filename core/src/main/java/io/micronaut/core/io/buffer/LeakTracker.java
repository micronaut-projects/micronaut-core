/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.io.buffer;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

// TODO: docs
public interface LeakTracker<T> {
    void close(@NonNull T trackedObject);

    interface Factory<T> {
        @Nullable
        LeakTracker<T> track(@NonNull T object);

        @NonNull
        static <T> Factory<T> forClass(@NonNull Class<T> trackedClass) {
            if (LeakTrackerFactoryHolder.nettyAvailable) {
                try {
                    return new NettyLeakTrackerFactory<>(trackedClass);
                } catch (LinkageError err) {
                    LeakTrackerFactoryHolder.nettyAvailable = false;
                }
            }
            // fallback: no tracking
            return obj -> null;
        }
    }
}

class LeakTrackerFactoryHolder {
    static boolean nettyAvailable = true;
}

class NettyLeakTrackerFactory<T> implements LeakTracker.Factory<T> {
    private final Class<T> trackedClass;
    private volatile ResourceLeakDetector<T> detector;

    static {
        // only allow initializing this class if netty is available
        //noinspection ResultOfMethodCallIgnored
        ResourceLeakDetector.class.getName();
    }

    NettyLeakTrackerFactory(Class<T> trackedClass) {
        this.trackedClass = trackedClass;
    }

    @Override
    public @Nullable LeakTracker<T> track(T object) {
        ResourceLeakDetector<T> detector = this.detector;
        if (detector == null) {
            synchronized (this) {
                detector = this.detector;
                if (detector == null) {
                    detector = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(trackedClass);
                    this.detector = detector;
                }
            }
        }
        ResourceLeakTracker<T> nettyTracker = detector.track(object);
        if (nettyTracker != null) {
            return new NettyLeakTracker<>(nettyTracker);
        } else {
            return null;
        }
    }

    private static final class NettyLeakTracker<T> implements LeakTracker<T> {
        private final ResourceLeakTracker<T> tracker;

        NettyLeakTracker(ResourceLeakTracker<T> tracker) {
            this.tracker = tracker;
        }

        @Override
        public void close(T trackedObject) {
            tracker.close(trackedObject);
        }
    }
}
