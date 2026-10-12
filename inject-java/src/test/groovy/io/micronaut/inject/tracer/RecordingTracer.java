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
 * that are not paired as documented.
 */
public class RecordingTracer implements BeanResolutionTracer {

    public final List<String> events = Collections.synchronizedList(new ArrayList<>());
    public final List<String> unpaired = Collections.synchronizedList(new ArrayList<>());
    private final ThreadLocal<Deque<Frame>> open = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public void traceBeanCreation(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition, Argument<?> beanType) {
        List<BeanDefinition<?>> beansInCreation = resolutionContext.getBeansInCreation();
        String parent = beansInCreation.isEmpty() ? "" : " <- " + beansInCreation.get(beansInCreation.size() - 1).getBeanType().getSimpleName();
        start("create " + beanDefinition.getBeanType().getSimpleName() + parent, "bean " + beanDefinition, isTraced(beanDefinition));
    }

    @Override
    public <T> void traceBeanCreated(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition) {
        end(null, "bean " + beanDefinition);
    }

    @Override
    public <T> void traceBeanCreationFailed(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, Throwable failure) {
        end("failed " + beanDefinition.getBeanType().getSimpleName() + ": " + failure.getClass().getSimpleName(), "bean " + beanDefinition);
    }

    @Override
    public <B, T> void traceInjectBean(BeanResolutionContext resolutionContext, BeanResolutionContext.Segment<B, T> segment) {
        String label = "inject " + segment.getDeclaringType().getBeanType().getSimpleName() + "." + segment.getName() + "(" + segment.getArgument().getName() + ")";
        start(label, "segment " + System.identityHashCode(segment), isTraced(segment.getDeclaringType()));
    }

    @Override
    public <B, T> void traceInjectComplete(BeanResolutionContext resolutionContext, BeanResolutionContext.Segment<B, T> segment) {
        end(null, "segment " + System.identityHashCode(segment));
    }

    /**
     * @return The events, joined into lines
     */
    public String eventsAsText() {
        synchronized (events) {
            return String.join("\n", events);
        }
    }

    private void start(String label, String key, boolean traced) {
        Deque<Frame> frames = open.get();
        if (traced) {
            events.add(indent(frames) + label);
        }
        frames.push(new Frame(key, traced));
    }

    private void end(String label, String key) {
        Deque<Frame> frames = open.get();
        Frame frame = frames.poll();
        if (frame == null || !frame.key.equals(key)) {
            unpaired.add(key + " closed while " + (frame == null ? "nothing" : frame.key) + " was open");
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

    private record Frame(String key, boolean traced) {
    }
}
