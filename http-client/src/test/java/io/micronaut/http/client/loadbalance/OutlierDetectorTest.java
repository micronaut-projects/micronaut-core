package io.micronaut.http.client.loadbalance;

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer.Outcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class OutlierDetectorTest {
    private static final ServiceInstance A = ServiceInstance.of("svc", URI.create("http://a:8080"));
    private static final ServiceInstance B = ServiceInstance.of("svc", URI.create("http://b:8080"));
    private static final ServiceInstance C = ServiceInstance.of("svc", URI.create("http://c:8080"));
    private static final List<ServiceInstance> ALL = List.of(A, B, C);

    // a nanoTime-like clock: an arbitrary large value, as System.nanoTime() gives
    private long now = Long.MAX_VALUE - TimeUnit.HOURS.toNanos(1);

    private OutlierDetector detector(int consecutiveFailures, int maxEjectionPercent) {
        OutlierDetectionConfiguration configuration = new OutlierDetectionConfiguration();
        configuration.setEnabled(true);
        configuration.setConsecutiveFailures(consecutiveFailures);
        configuration.setBaseEjectionTime(Duration.ofSeconds(10));
        configuration.setMaxEjectionTime(Duration.ofSeconds(25));
        configuration.setMaxEjectionPercent(maxEjectionPercent);
        return new OutlierDetector(configuration, () -> now);
    }

    private void advance(long seconds) {
        now += TimeUnit.SECONDS.toNanos(seconds);
    }

    @Test
    void consecutiveFailuresEjectAnInstance() {
        OutlierDetector detector = detector(2, 100);
        Assertions.assertEquals(ALL, detector.available(ALL));
        detector.report(A, Outcome.CONNECT_FAILURE);
        Assertions.assertEquals(ALL, detector.available(ALL), "One failure is not enough");
        detector.report(A, Outcome.TIMEOUT);
        Assertions.assertEquals(List.of(B, C), detector.available(ALL));
        Assertions.assertTrue(detector.isEjected(A));
    }

    @Test
    void aSuccessResetsTheCount() {
        OutlierDetector detector = detector(2, 100);
        detector.report(A, Outcome.CONNECT_FAILURE);
        detector.report(A, Outcome.SUCCESS);
        detector.report(A, Outcome.CONNECT_FAILURE);
        Assertions.assertEquals(ALL, detector.available(ALL));
    }

    @Test
    void serverErrorsDoNotCountUnlessConfigured() {
        OutlierDetector detector = detector(1, 100);
        detector.report(A, Outcome.SERVER_ERROR);
        detector.report(A, Outcome.SERVER_ERROR);
        Assertions.assertEquals(ALL, detector.available(ALL));

        OutlierDetectionConfiguration configuration = new OutlierDetectionConfiguration();
        configuration.setEnabled(true);
        configuration.setConsecutiveServerErrors(2);
        OutlierDetector counting = new OutlierDetector(configuration, () -> now);
        counting.available(ALL);
        counting.report(A, Outcome.SERVER_ERROR);
        Assertions.assertFalse(counting.isEjected(A));
        counting.report(A, Outcome.SERVER_ERROR);
        Assertions.assertTrue(counting.isEjected(A));
    }

    @Test
    void ejectionEndsAndGrowsWithEachEjection() {
        OutlierDetector detector = detector(1, 100);
        detector.available(ALL);
        detector.report(A, Outcome.RESET);
        Assertions.assertTrue(detector.isEjected(A));
        advance(9);
        Assertions.assertTrue(detector.isEjected(A), "Ejected for the base time");
        advance(2);
        Assertions.assertFalse(detector.isEjected(A), "Back after the base time");

        // the second ejection lasts twice the base time
        detector.report(A, Outcome.RESET);
        advance(19);
        Assertions.assertTrue(detector.isEjected(A));
        advance(2);
        Assertions.assertFalse(detector.isEjected(A));

        // the third is capped by the maximum
        detector.report(A, Outcome.RESET);
        advance(24);
        Assertions.assertTrue(detector.isEjected(A));
        advance(2);
        Assertions.assertFalse(detector.isEjected(A));

        // a success after being tried again resets the multiplier
        detector.report(A, Outcome.SUCCESS);
        detector.report(A, Outcome.SUCCESS);
        detector.report(A, Outcome.RESET);
        advance(11);
        Assertions.assertFalse(detector.isEjected(A), "Ejected for the base time again");
    }

    @Test
    void noMoreThanTheMaximumShareIsEjected() {
        OutlierDetector detector = detector(1, 50);
        detector.available(ALL);
        detector.report(A, Outcome.CONNECT_FAILURE);
        detector.report(B, Outcome.CONNECT_FAILURE);
        Assertions.assertTrue(detector.isEjected(A));
        Assertions.assertFalse(detector.isEjected(B), "Ejecting B too would eject 2 of 3 instances");
        Assertions.assertEquals(List.of(B, C), detector.available(ALL));
    }

    @Test
    void concurrentFailuresDoNotExceedTheMaximumShare() throws Exception {
        // half of the instances may be ejected: every one failing at the same time must not eject more
        int instances = 8;
        List<ServiceInstance> all = new ArrayList<>();
        for (int i = 0; i < instances; i++) {
            all.add(ServiceInstance.of("svc", URI.create("http://instance-" + i + ":8080")));
        }
        ExecutorService executor = Executors.newFixedThreadPool(instances);
        try {
            for (int iteration = 0; iteration < 3000; iteration++) {
                OutlierDetector detector = detector(1, 50);
                detector.available(all);
                // the threads reach the ejection decision together
                CyclicBarrier barrier = new CyclicBarrier(instances);
                List<Future<?>> reports = new ArrayList<>();
                for (ServiceInstance instance : all) {
                    reports.add(executor.submit(() -> {
                        barrier.await();
                        detector.report(instance, Outcome.CONNECT_FAILURE);
                        return null;
                    }));
                }
                for (Future<?> report : reports) {
                    report.get(10, TimeUnit.SECONDS);
                }
                long ejected = all.stream().filter(detector::isEjected).count();
                Assertions.assertTrue(ejected <= instances / 2, "In iteration " + iteration + ", " + ejected + " of " + instances + " instances are ejected, the maximum share is half");
                Assertions.assertEquals(instances / 2, ejected, "The maximum share is ejected");
                Assertions.assertEquals(instances / 2, detector.available(all).size());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anInstanceThatLeftDoesNotTakeAnEjectionPlace() {
        OutlierDetector detector = detector(1, 50);
        List<ServiceInstance> before = List.of(A, B);
        detector.available(before);
        detector.report(A, Outcome.CONNECT_FAILURE);
        Assertions.assertTrue(detector.isEjected(A));

        // discovery replaces A with C while A is still ejected: of B and C, one may be ejected
        List<ServiceInstance> after = List.of(B, C);
        Assertions.assertEquals(after, detector.available(after));
        detector.report(B, Outcome.CONNECT_FAILURE);
        Assertions.assertTrue(detector.isEjected(B), "A left, so it does not count against the maximum share");
        Assertions.assertEquals(List.of(C), detector.available(after));

        // a late report for A, which left, is not counted against the instances that are members
        detector.report(A, Outcome.CONNECT_FAILURE);
        detector.report(C, Outcome.CONNECT_FAILURE);
        Assertions.assertFalse(detector.isEjected(C), "Ejecting C too would eject both members");
        Assertions.assertEquals(List.of(C), detector.available(after));
    }

    @Test
    void everyInstanceEjectedMeansNoneIs() {
        OutlierDetector detector = detector(1, 100);
        detector.available(ALL);
        for (ServiceInstance instance : ALL) {
            detector.report(instance, Outcome.CONNECT_FAILURE);
        }
        Assertions.assertEquals(ALL, detector.available(ALL));
    }
}
