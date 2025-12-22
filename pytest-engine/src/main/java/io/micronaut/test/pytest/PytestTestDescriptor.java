/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.test.pytest;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Test descriptor for an individual Python test function or method.
 */
public class PytestTestDescriptor extends AbstractTestDescriptor {

    public static final String SEGMENT_SOURCE = "source";
    public static final String SEGMENT_TEST = "test";
    private final Path filePath;
    private final int startLine;
    private final int endLine;
    private final int startColumn;
    private final int endColumn;

    public PytestTestDescriptor(UniqueId uniqueId, String displayName, TestSource source,
                               Path filePath, int startLine, int endLine, int startColumn, int endColumn) {
        super(uniqueId, displayName, source);
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    public Path getFilePath() {
        return filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public boolean matchesId(String testId) {
        UniqueId uniqueId = getUniqueId();
        List<UniqueId.Segment> segments = uniqueId.getSegments();
        UniqueId.Segment source = segments.get(1);
        UniqueId.Segment test = segments.get(2);
        String nameToMatch =  "/" + source.getValue() + "::" + test.getValue();
        return testId.endsWith(nameToMatch);
    }
}
