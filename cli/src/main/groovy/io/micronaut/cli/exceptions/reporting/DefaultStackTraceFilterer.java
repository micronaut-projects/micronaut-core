/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.exceptions.reporting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default implementation of StackTraceFilterer.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultStackTraceFilterer implements StackTraceFilterer {
    public static final String STACK_LOG_NAME = "StackTrace";
    public static final Log STACK_LOG = LogFactory.getLog(STACK_LOG_NAME);

    private static final String[] DEFAULT_INTERNAL_PACKAGES = new String[]{
        "org.codehaus.groovy.runtime.",
        "org.codehaus.groovy.reflection.",
        "org.codehaus.groovy.ast.",
        "org.springframework.web.filter",
        "org.springframework.boot.actuate",
        "org.mortbay.",
        "groovy.lang.",
        "org.apache.catalina.",
        "org.apache.coyote.",
        "org.apache.tomcat.",
        "net.sf.cglib.proxy.",
        "sun.",
        "java.lang.reflect.",
        "org.springsource.loaded.",
        "com.opensymphony.",
        "javax.servlet."
    };

    private List<String> packagesToFilter = new ArrayList<String>();
    private boolean shouldFilter;
    private String cutOffPackage = null;

    /**
     * Default constructor.
     */
    public DefaultStackTraceFilterer() {
        this(!Boolean.getBoolean(SYS_PROP_DISPLAY_FULL_STACKTRACE));
    }

    /**
     * @param shouldFilter Whether should filter
     */
    public DefaultStackTraceFilterer(boolean shouldFilter) {
        this.shouldFilter = shouldFilter;
        packagesToFilter.addAll(Arrays.asList(DEFAULT_INTERNAL_PACKAGES));
    }

    @Override
    public void addInternalPackage(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Package name cannot be null");
        }
        packagesToFilter.add(name);
    }

    @Override
    public void setCutOffPackage(String cutOffPackage) {
        this.cutOffPackage = cutOffPackage;
    }

    @Override
    public Throwable filter(Throwable source, boolean recursive) {
        if (recursive) {
            Throwable current = source;
            while (current != null) {
                current = filter(current);
                current = current.getCause();
            }
        }
        return filter(source);
    }

    @Override
    public Throwable filter(Throwable source) {
        if (shouldFilter) {
            StackTraceElement[] trace = source.getStackTrace();
            List<StackTraceElement> newTrace = filterTraceWithCutOff(trace, cutOffPackage);

            if (newTrace.isEmpty()) {
                // filter with no cut-off so at least there is some trace
                newTrace = filterTraceWithCutOff(trace, null);
            }

            // Only trim the trace if there was some application trace on the stack
            // if not we will just skip sanitizing and leave it as is
            if (!newTrace.isEmpty()) {
                // We don't want to lose anything, so log it
                STACK_LOG.error(FULL_STACK_TRACE_MESSAGE, source);
                StackTraceElement[] clean = new StackTraceElement[newTrace.size()];
                newTrace.toArray(clean);
                source.setStackTrace(clean);
            }
        }
        return source;
    }

    @Override
    public void setShouldFilter(boolean shouldFilter) {
        this.shouldFilter = shouldFilter;
    }

    /**
     * Whether the given class name is an internal class and should be filtered.
     *
     * @param className The class name
     * @return true if is internal
     */
    protected boolean isApplicationClass(String className) {
        for (String packageName : packagesToFilter) {
            if (className.startsWith(packageName)) {
                return false;
            }
        }
        return true;
    }

    private List<StackTraceElement> filterTraceWithCutOff(StackTraceElement[] trace, String endPackage) {
        List<StackTraceElement> newTrace = new ArrayList<StackTraceElement>();
        boolean foundGroovy = false;
        for (StackTraceElement stackTraceElement : trace) {
            String className = stackTraceElement.getClassName();
            String fileName = stackTraceElement.getFileName();
            if (!foundGroovy && fileName != null && fileName.endsWith(".groovy")) {
                foundGroovy = true;
            }
            if (endPackage != null && className.startsWith(endPackage) && foundGroovy) {
                break;
            }
            if (isApplicationClass(className)) {
                if (stackTraceElement.getLineNumber() > -1) {
                    newTrace.add(stackTraceElement);
                }
            }
        }
        return newTrace;
    }
}
