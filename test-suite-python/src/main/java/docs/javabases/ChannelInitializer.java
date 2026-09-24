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
package docs.javabases;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Java base whose overridable method declares a checked exception, in the shape of a messaging
 * channel initializer.
 */
public abstract class ChannelInitializer {

    private final List<String> declared = new ArrayList<>();

    public void initialize(String channel, String name) throws IOException {
        if (channel.isBlank()) {
            throw new IOException("No channel for " + name);
        }
        declared.add(channel + "/" + name);
    }

    public List<String> declared() {
        return List.copyOf(declared);
    }

    public String initializeAll(List<String> names) {
        try {
            for (String name : names) {
                initialize("main", name);
            }
            return "ok";
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }
}
