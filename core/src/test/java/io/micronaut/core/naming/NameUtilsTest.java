package io.micronaut.core.naming;

import io.micronaut.core.util.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Disabled
class NameUtilsTest {
    private static final Pattern SERVICE_ID_REGEX = Pattern.compile("[\\p{javaLowerCase}\\d-]+");
    private static final Pattern KEBAB_REPLACEMENTS = Pattern.compile("[_ ]");

    public static boolean oldIsHyphenatedLowerCase(String name) {
        return StringUtils.isNotEmpty(name) && SERVICE_ID_REGEX.matcher(name).matches() && Character.isLetter(name.charAt(0));
    }

    private static String oldHyphenate(String name, boolean lowerCase) {
        if (NameUtils.isHyphenatedLowerCase(name)) {
            return KEBAB_REPLACEMENTS.matcher(name).replaceAll("-");
        } else {
            char separatorChar = '-';
            return NameUtils.separateCamelCase(KEBAB_REPLACEMENTS.matcher(name).replaceAll("-"), lowerCase, separatorChar);
        }
    }

    @Test
    public void isHyphenatedLowerCase() {
        new ParallelTester() {
            @Override
            void test(String s) {
                Assertions.assertEquals(oldIsHyphenatedLowerCase(s), NameUtils.isHyphenatedLowerCase(s));
            }
        }.testParallel();
    }

    private static abstract class ParallelTester {
        static final String TEST_CHARS = " !\"#$%&'()*+,-./012:;<=>?@ABC[\\]^_`abc{|}~äö\uD801\uDC37";
        static final int TEST_LENGTH = 6;
        static final int PROGRESS_STEP = 1024;

        final int[] testPoints = IntStream.concat(IntStream.of(0), TEST_CHARS.codePoints()).toArray();

        abstract void test(String s);

        final void test(long start, long end, LongAdder progress) {
            long remaining = end - start;
            int[] pointIndices = new int[TEST_LENGTH];
            for (int i = 0; i < TEST_LENGTH; i++) {
                pointIndices[i] = (int) (start % testPoints.length);
                start /= testPoints.length;
            }
            StringBuilder builder = new StringBuilder(TEST_LENGTH);
            while (true) {
                builder.setLength(0);
                for (int i = 0; i < TEST_LENGTH; i++) {
                    int point = testPoints[pointIndices[i]];
                    if (point != 0) {
                        builder.appendCodePoint(point);
                    }
                }
                test(builder.toString());

                if (remaining % PROGRESS_STEP == 0) {
                    progress.add(PROGRESS_STEP);
                }

                if (remaining-- == 0) {
                    break;
                }

                for (int i = 0; ; i++) {
                    int nextIndex = pointIndices[i] + 1;
                    if (nextIndex == testPoints.length) {
                        pointIndices[i] = 0; // increment next index
                    } else {
                        pointIndices[i] = nextIndex;
                        break;
                    }
                }
            }
        }

        final void testParallel() {
            int threads = Runtime.getRuntime().availableProcessors();
            ExecutorService service = Executors.newFixedThreadPool(threads);
            try {
                long count = (long) Math.pow(testPoints.length, TEST_LENGTH);
                List<CompletableFuture<?>> futures = new ArrayList<>();
                LongAdder progress = new LongAdder();
                for (int i = 0; i < threads; i++) {
                    long start = count / threads * i;
                    long end = i == threads - 1 ? count : start + count / threads;
                    futures.add(CompletableFuture.runAsync(() -> test(start, end, progress), service));
                }
                CompletableFuture<Void> combined = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                while (!combined.isDone()) {
                    System.out.println("Progress: " + (100 * progress.sum() / count) + "% (" + progress.sum() + ")");
                    TimeUnit.SECONDS.sleep(1);
                }
                combined.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                service.shutdown();
            }
        }
    }
}
