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
package io.micronaut.web.router.processor;

import io.micronaut.http.uri.ParsedRouteTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers the templates of the test colon engine, registered with the service loader of the tests:
 * a literal segment is compared, a {@code :name} segment accepts any segment that is not empty,
 * exactly as the pattern of the engine does. It reads the engine's own structure of the parsed
 * template, not the text of the template.
 */
public class ColonRouteTemplateCompiler implements RouteTemplateCompiler {

    private final String engineId;

    public ColonRouteTemplateCompiler() {
        this(ColonRouteTemplateEngine.ID);
    }

    protected ColonRouteTemplateCompiler(String engineId) {
        this.engineId = engineId;
    }

    @Override
    public String engineId() {
        return engineId;
    }

    @Override
    public String engineVersion() {
        return "1";
    }

    @Override
    public LoweredTemplate lower(ParsedRouteTemplate template) {
        if (!(template instanceof ColonRouteTemplateEngine.Parsed parsed)) {
            return LoweredTemplate.unsupported("not a colon template");
        }
        List<LoweredTemplate.Segment> segments = new ArrayList<>();
        List<String> captures = new ArrayList<>();
        for (ColonRouteTemplateEngine.Segment segment : parsed.segments()) {
            if (segment.variable()) {
                segments.add(LoweredTemplate.Segment.variable(LoweredTemplate.VariableRule.NON_EMPTY_SEGMENT));
                captures.add(segment.text());
            } else if (segment.text().isEmpty() || segment.text().indexOf('/') >= 0) {
                return LoweredTemplate.unsupported("an empty literal segment");
            } else {
                segments.add(LoweredTemplate.Segment.literal(segment.text()));
            }
        }
        return LoweredTemplate.of(segments, captures);
    }

    /**
     * The compiler of {@link ColonRouteTemplateEngine.Ordered}.
     */
    public static final class Ordered extends ColonRouteTemplateCompiler {
        public Ordered() {
            super(ColonRouteTemplateEngine.Ordered.ORDERED_ID);
        }
    }

    /**
     * The compiler of {@link ColonRouteTemplateEngine.Matrix}.
     */
    public static final class Matrix extends ColonRouteTemplateCompiler {
        public Matrix() {
            super(ColonRouteTemplateEngine.Matrix.MATRIX_ID);
        }
    }

    /**
     * The compiler of {@link ColonRouteTemplateEngine.Selecting}.
     */
    public static final class Selecting extends ColonRouteTemplateCompiler {
        public Selecting() {
            super(ColonRouteTemplateEngine.Selecting.SELECTING_ID);
        }
    }
}
