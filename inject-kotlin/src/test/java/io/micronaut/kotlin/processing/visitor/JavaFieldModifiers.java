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
package io.micronaut.kotlin.processing.visitor;

/**
 * Fields of every Java kind, read by the KSP processor from the classpath.
 */
@SuppressWarnings("unused")
public class JavaFieldModifiers {
    public static final String CONSTANT = "c";
    public static String staticField;
    public String publicField;
    public final String publicFinalField = "f";
    protected String protectedField;
    String packagePrivateField;
    private String privateField;
    public transient String transientField;
    public volatile String volatileField;
}
