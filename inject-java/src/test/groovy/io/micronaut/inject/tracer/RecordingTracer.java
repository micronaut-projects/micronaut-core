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
package io.micronaut.inject.tracer;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.BeanResolutionTracer;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Records the creations and injections of the beans of this package, indented by their nesting, and the callbacks
 * that are not paired or ordered as documented.
 */
public class RecordingTracer implements BeanResolutionTracer {

    public final List<String> events = Collections.synchronizedList(new ArrayList<>());
    public final List<String> violations = Collections.synchronizedList(new ArrayList<>());
    private final ThreadLocal<Deque<Frame>> open = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public void traceBeanCreation(Resolution resolution, BeanDefinition<?> beanDefinition, Argument<?> beanType) {
        if (resolution instanceof BeanResolutionContext || resolution.getBeanContext() == null) {
            violations.add("the resolution exposes the resolution context");
        }
        List<BeanDefinition<?>> beansInCreation = resolution.getBeansInCreation();
        String parent = beansInCreation.isEmpty() ? "" : " <- " + beansInCreation.get(beansInCreation.size() - 1).getBeanType().getSimpleName();
        start("create " + beanDefinition.getBeanType().getSimpleName() + parent, beanDefinition, isTraced(beanDefinition));
    }

    @Override
    public <T> void traceBeanCreated(Resolution resolution, BeanDefinition<T> beanDefinition) {
        end(null, beanDefinition);
    }

    @Override
    public <T> void traceBeanCreationFailed(Resolution resolution, BeanDefinition<T> beanDefinition, Throwable failure) {
        end("failed " + beanDefinition.getBeanType().getSimpleName() + ": " + failure.getClass().getSimpleName(), beanDefinition);
    }

    @Override
    public void traceInjectBean(Resolution resolution, Injection injection) {
        String label = "inject " + injection.getKind() + " " + injection.getDeclaringBean().getBeanType().getSimpleName() + "." + injection.getMemberName() + "(" + injection.getArgument().getName() + ")";
        if (resolution.getInjectionPath().get(resolution.getInjectionPath().size() - 1) != injection) {
            violations.add(label + " is not the innermost injection");
        }
        start(label, injection, isTraced(injection.getDeclaringBean()));
    }

    @Override
    public void traceInjectComplete(Resolution resolution, Injection injection) {
        end(null, injection);
    }

    /**
     * @return The events, joined into lines
     */
    public String eventsAsText() {
        synchronized (events) {
            return String.join("\n", events);
        }
    }

    private void start(String label, Object key, boolean traced) {
        Deque<Frame> frames = open.get();
        if (traced) {
            events.add(indent(frames) + label);
        }
        frames.push(new Frame(key, traced));
    }

    private void end(String label, Object key) {
        Deque<Frame> frames = open.get();
        Frame frame = frames.poll();
        if (frame == null || frame.key != key) {
            violations.add(key + " closed while " + (frame == null ? "nothing" : frame.key) + " was open");
            return;
        }
        if (label != null && frame.traced) {
            events.add(indent(frames) + label);
        }
    }

    private static String indent(Deque<Frame> frames) {
        int depth = 0;
        for (Frame frame : frames) {
            if (frame.traced) {
                depth++;
            }
        }
        return "  ".repeat(depth);
    }

    private static boolean isTraced(BeanDefinition<?> definition) {
        return definition.getBeanType().getPackageName().equals(RecordingTracer.class.getPackageName());
    }

    private record Frame(Object key, boolean traced) {
    }
}
