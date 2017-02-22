package almworks;

import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Created by Aleksei Grishkov on 22.01.2017.
 */
public class EntityLockTest {

    private static final int ENTITY_COUNT = 1000;
    private static final int THREAD_COUNT = 20;
    private static final int ATTEMPTS = 1000;
    private static final int DELTA = 1000;
    private static final int BYTES_IN_MB = 1024 * 1024;

    @Ignore
    @Test
    public void testNoLock() throws InterruptedException {
        ConcurrentMap<Big, Counter> counterMap = getCounterMap();

        List<Callable<Object>> callables = Stream.generate(() -> (Callable<Object>) () -> {
            for (int i = 0; i < ATTEMPTS; i++) {
                int id = ThreadLocalRandom.current().nextInt(ENTITY_COUNT);
                counterMap.get(new Big(id)).add(DELTA);
            }
            return null;
        }).limit(THREAD_COUNT).collect(Collectors.toList());

        long t1 = System.nanoTime();
        Executors.newFixedThreadPool(THREAD_COUNT).invokeAll(callables);
        System.out.println("no lock - " + (System.nanoTime() - t1) / 1000 + " microseconds");
        testCounterMapValid(counterMap);
    }

    @Test
    public void testWithConcurrentHashMapLock() throws InterruptedException {
        ConcurrentMap<Big, Counter> counterMap = testWithLock(new ConcurrentHashMapEntityLocker<>());
        testCounterMapValid(counterMap);
    }

    @Test
    public void testWithLinkedHashMapLock() throws InterruptedException {
        EntityLockerImpl<Big> entityLocker = new EntityLockerImpl<>();
        System.out.println(entityLocker.lockCount());
        ConcurrentMap<Big, Counter> counterMap = testWithLock(entityLocker);
        System.out.println(entityLocker.lockCount());
//        entityLocker.printLocks();
        testCounterMapValid(counterMap);
    }

    @Ignore
    @Test
    public void testWithWeakHashMapLock() throws InterruptedException {
        ConcurrentMap<Big, Counter> counterMap = testWithLock(new WeakHashMapEntityLocker<>());
        testCounterMapValid(counterMap);
    }

    private void testCounterMapValid(ConcurrentMap<Big, Counter> counterMap) {
        int changedCounters = 0;
        for (Counter counter : counterMap.values()) {
            if (counter.i > 0) {
                changedCounters++;
                assertEquals(counter.i, counter.check.get());
            }
        }
        System.out.printf("changedCounters: %d of %d%n", changedCounters, counterMap.size());
        assertTrue(changedCounters > counterMap.size() / 2);
    }

    private void testCounterMapInvalid(ConcurrentMap<Integer, Counter> counterMap) {
        int validCounters = 0;
        for (Counter counter : counterMap.values()) {
            assertTrue(counter.i > 0);
            if (counter.i == counter.check.get()) {
                validCounters++;
            }
        }
        assertNotEquals(counterMap.size(), validCounters);
    }

    private ConcurrentMap<Big, Counter> testWithLock(EntityLocker<Big> entityLocker) throws InterruptedException {
        cleanMemory();
        System.out.printf("Free memory before: %dmb%n", Runtime.getRuntime().freeMemory() / BYTES_IN_MB);

        ConcurrentMap<Big, Counter> counterMap = getCounterMap();

        List<Callable<Object>> callables = Stream.generate(() -> (Callable<Object>) () -> {
            int i;
            for (i = 0; i < ATTEMPTS; i++) {
                try {
                    Integer id = ThreadLocalRandom.current().nextInt(ENTITY_COUNT);
                    Big key = new Big(id);
                    Counter counter = counterMap.get(key);
                    entityLocker.withLock(key, new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            counter.add(DELTA);
                            Thread.sleep(ThreadLocalRandom.current().nextInt(100));
                            return null;
                        }
                    });
//                    Thread.sleep(ThreadLocalRandom.current().nextInt(10));
                }
                catch (Exception ignored) {}
            }
            return null;
        }).limit(THREAD_COUNT).collect(Collectors.toList());

        long t1 = System.nanoTime();
        Executors.newFixedThreadPool(THREAD_COUNT).invokeAll(callables);
        System.out.println("using lock - " + (System.nanoTime() - t1) / 1_000_000 + " millis");

        cleanMemory();
        System.out.printf("Free memory after %dmb%n", Runtime.getRuntime().freeMemory() / BYTES_IN_MB);

        return counterMap;
    }

    private ConcurrentMap<Big, Counter> getCounterMap() {
        return IntStream.range(0, ENTITY_COUNT).mapToObj(Big::new).collect(Collectors.toConcurrentMap(o -> o, o -> new Counter(o.id)));
//        return IntStream.range(0, ENTITY_COUNT).boxed().collect(Collectors.toConcurrentMap(o -> o, Counter::new));
    }

    private static class Counter {
        private final int id;
        private int i = 0;
        private final AtomicInteger check = new AtomicInteger();

        public Counter(Integer id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void add(int delta) {
            for (int j = 0; j < delta; j++) {
                i++;
                check.incrementAndGet();
            }
        }
    }

    @Test
    @Ignore
    public void factoryTest() throws Exception {
        EntityLocker entityLocker = EntityLockerFactory.getEntityLocker(Integer.class);
        System.out.println(entityLocker);
        entityLocker.withLock(1, () -> {
            System.out.println("test");
            return null;
        });
    }

    private class Big {
        int id;
        double[] payload = new double[10000];

        public Big(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Big big = (Big) o;

            return id == big.id;

        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    private void cleanMemory() throws InterruptedException {
        for (int i = 0; i < 2; i++) {
            System.gc();
            Thread.sleep(1000);
        }
    }
}
