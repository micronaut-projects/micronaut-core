package io.micronaut.dev.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the events of a run.
 */
final class RecordingListener implements TestEventListener {

    final List<String> events = new ArrayList<>();
    final Map<String, TestOutcome> outcomes = new LinkedHashMap<>();
    final Map<String, TestId> tests = new LinkedHashMap<>();
    final Map<String, StringBuilder> output = new LinkedHashMap<>();
    TestRunStarted started;
    TestRunSummary summary;

    @Override
    public void runStarted(TestRunStarted event) {
        started = event;
        events.add("runStarted");
    }

    @Override
    public void testStarted(TestId test) {
        events.add("started " + key(test));
    }

    @Override
    public void output(TestId test, TestOutput stream, String text) {
        output.computeIfAbsent(key(test) + " " + stream, k -> new StringBuilder()).append(text);
    }

    @Override
    public void testFinished(TestId test, TestOutcome outcome) {
        events.add("finished " + key(test));
        outcomes.put(key(test), outcome);
        tests.put(key(test), test);
    }

    @Override
    public void runFinished(TestRunSummary summary) {
        this.summary = summary;
        events.add("runFinished");
    }

    static String key(TestId test) {
        return test.className() + "." + test.name();
    }

    TestStatus status(String key) {
        TestOutcome outcome = outcomes.get(key);
        return outcome == null ? null : outcome.status();
    }
}
