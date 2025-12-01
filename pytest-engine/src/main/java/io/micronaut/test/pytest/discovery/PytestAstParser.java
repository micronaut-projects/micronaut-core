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
package io.micronaut.test.pytest.discovery;

import io.micronaut.test.pytest.PytestFileDescriptor;
import io.micronaut.test.pytest.PytestTestDescriptor;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses Python files using AST to discover test functions and classes.
 * Uses GraalPy to execute Python AST parsing code.
 */
public class PytestAstParser {

    private static final Logger LOG = LoggerFactory.getLogger(PytestAstParser.class);

    private static final String AST_PARSER_CODE = """
import ast
import sys
from typing import List, Tuple, Any

class TestDiscoveryVisitor(ast.NodeVisitor):
    def __init__(self):
        self.tests = []
        self.current_class = None

    def visit_FunctionDef(self, node):
        # Check if function name starts with 'test_'
        if node.name.startswith('test_'):
            test_info = (
                node.name,  # name
                'function',  # type
                self.current_class,  # class
                node.lineno,  # line
                getattr(node, 'end_lineno', node.lineno),  # end_line
                getattr(node, 'col_offset', 0),  # col
                getattr(node, 'end_col_offset', 0)  # end_col
            )
            self.tests.append(test_info)
        self.generic_visit(node)

def discover_tests(source_code: str) -> List[Tuple]:
    try:
        tree = ast.parse(source_code)
        visitor = TestDiscoveryVisitor()
        visitor.visit(tree)
        return visitor.tests
    except SyntaxError as e:
        print(f"Syntax error in Python code: {e}", file=sys.stderr)
        return []
    except Exception as e:
        print(f"Error parsing Python AST: {e}", file=sys.stderr)
        return []
""";

    private final Context context;

    public PytestAstParser() {
        this.context = Context.newBuilder("python")
                .allowAllAccess(true)
                .build();
        initializeParser();
    }

    private void initializeParser() {
        try {
            context.eval(Source.newBuilder("python", AST_PARSER_CODE, "ast_parser.py").build());
        } catch (Exception e) {
            LOG.error("Failed to initialize Python AST parser", e);
            throw new RuntimeException("Failed to initialize Python AST parser", e);
        }
    }

    /**
     * Parses a Python file and returns a TestDescriptor for the file and its tests.
     */
    public TestDescriptor parsePythonFile(Path filePath, boolean isTestFile) {
        LOG.trace("Parsing Python file: {}", filePath);

        try {
            String sourceCode = Files.readString(filePath);
            LOG.trace("Source code length: {}", sourceCode.length());
            List<Map<String, Object>> tests = discoverTests(sourceCode);
            LOG.trace("Discovered {} raw tests from AST", tests.size());
            for (Map<String, Object> test : tests) {
                LOG.trace("Test found: {}", test);
            }

            if (tests.isEmpty()) {
                LOG.trace("No tests found in file: {}", filePath);
                return null;
            }

            // Create file descriptor
            UniqueId fileId = UniqueId.forEngine("pytest-engine").append("file", filePath.toString());
            FileSource fileSource = FileSource.from(filePath.toFile());
            PytestFileDescriptor fileDescriptor = new PytestFileDescriptor(fileId, filePath.getFileName().toString(), fileSource);

            // Add test descriptors
            for (Map<String, Object> test : tests) {
                addTestDescriptor(fileDescriptor, filePath, test);
            }

            LOG.trace("Found {} tests in file: {}", fileDescriptor.getChildren().size(), filePath);
            return fileDescriptor;

        } catch (IOException e) {
            LOG.error("Error reading Python file: {}", filePath, e);
            return null;
        }
    }

    /**
     * Parses a specific Python method and returns its TestDescriptor.
     */
    public TestDescriptor parsePythonMethod(Path filePath, String methodName, boolean isTestFile) {
        LOG.trace("Parsing Python method: {}::{}", filePath, methodName);

        TestDescriptor fileDescriptor = parsePythonFile(filePath, isTestFile);
        if (fileDescriptor == null) {
            return null;
        }

        // Find the specific method
        return fileDescriptor.getChildren().stream()
                .filter(child -> child.getDisplayName().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> discoverTests(String sourceCode) {
        try {
            Value discoverFunction = context.getBindings("python").getMember("discover_tests");
            if (discoverFunction == null) {
                LOG.error("discover_tests function not found in Python context");
                return Collections.emptyList();
            }

            Value result = discoverFunction.execute(sourceCode);
            LOG.trace("Python result type: {}, hasArrayElements: {}", result.getClass().getSimpleName(), result.hasArrayElements());

            if (!result.hasArrayElements()) {
                LOG.error("discover_tests did not return an array");
                return Collections.emptyList();
            }

            List<Map<String, Object>> tests = new ArrayList<>();
            for (int i = 0; i < result.getArraySize(); i++) {
                Value testValue = result.getArrayElement(i);
                LOG.trace("Processing test {}: type={}, hasMembers={}", i, testValue.getClass().getSimpleName(), testValue.hasMembers());
                Map<String, Object> test = convertValueToMap(testValue);
                LOG.trace("Converted test {}: {}", i, test);
                tests.add(test);
            }

            return tests;

        } catch (Exception e) {
            LOG.error("Error discovering tests in Python code", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertValueToMap(Value value) {
        Map<String, Object> map = new HashMap<>();
        if (value.hasArrayElements()) {
            // Handle tuple format: (name, type, class, line, end_line, col, end_col[, methods])
            try {
                long size = value.getArraySize();
                if (size >= 7) {
                    // Extract tuple elements
                    Value nameValue = value.getArrayElement(0);
                    Value typeValue = value.getArrayElement(1);
                    Value classValue = value.getArrayElement(2);
                    Value lineValue = value.getArrayElement(3);
                    Value endLineValue = value.getArrayElement(4);
                    Value colValue = value.getArrayElement(5);
                    Value endColValue = value.getArrayElement(6);

                    String name = nameValue.isString() ? nameValue.asString() : null;
                    String type = typeValue.isString() ? typeValue.asString() : null;
                    String className = classValue.isNull() ? null : (classValue.isString() ? classValue.asString() : null);
                    Integer line = lineValue.isNumber() && lineValue.fitsInInt() ? lineValue.asInt() : null;
                    Integer endLine = endLineValue.isNumber() && endLineValue.fitsInInt() ? endLineValue.asInt() : null;
                    Integer col = colValue.isNumber() && colValue.fitsInInt() ? colValue.asInt() : null;
                    Integer endCol = endColValue.isNumber() && endColValue.fitsInInt() ? endColValue.asInt() : null;

                    map.put("name", name);
                    map.put("type", type);
                    map.put("class", className);
                    map.put("line", line);
                    map.put("end_line", endLine);
                    map.put("col", col);
                    map.put("end_col", endCol);

                    // Handle methods for class tests
                    if (size > 7 && "class".equals(type)) {
                        Value methodsValue = value.getArrayElement(7);
                        if (methodsValue.hasArrayElements()) {
                            List<Map<String, Object>> methods = new ArrayList<>();
                            for (int i = 0; i < methodsValue.getArraySize(); i++) {
                                Value methodTuple = methodsValue.getArrayElement(i);
                                methods.add(convertValueToMap(methodTuple));
                            }
                            map.put("methods", methods);
                        }
                    }

                    LOG.trace("Converted tuple to map: {}", map);
                } else {
                    LOG.warn("Tuple has insufficient elements: {}", size);
                }
            } catch (Exception e) {
                LOG.error("Error converting tuple to map", e);
            }
        } else {
            LOG.trace("Value is not an array/tuple");
        }
        return map;
    }

    private void addTestDescriptor(PytestFileDescriptor fileDescriptor, Path filePath, Map<String, Object> test) {
        String testName = (String) test.get("name");
        String testType = (String) test.get("type");
        Integer line = (Integer) test.get("line");
        Integer endLine = (Integer) test.get("end_line");
        Integer col = (Integer) test.get("col");
        Integer endCol = (Integer) test.get("end_col");

        if (testName == null || testName.trim().isEmpty()) {
            LOG.warn("Skipping test with null/empty name: {}", test);
            return;
        }

        UniqueId testId = fileDescriptor.getUniqueId().append("test", testName);
        TestSource testSource = MethodSource.from(filePath.toString(), testName);

        PytestTestDescriptor testDescriptor = new PytestTestDescriptor(
                testId,
                testName,
                testSource,
                filePath,
                line != null ? line : 1,
                endLine != null ? endLine : 1,
                col != null ? col : 0,
                endCol != null ? endCol : 0
        );

        fileDescriptor.addChild(testDescriptor);

        // Handle class methods
        if ("class".equals(testType)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) test.get("methods");
            if (methods != null) {
                for (Map<String, Object> method : methods) {
                    addTestDescriptor(fileDescriptor, filePath, method);
                }
            }
        }
    }
}
