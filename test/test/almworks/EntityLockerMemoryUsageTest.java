package almworks;

import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Aleksei Grishkov on 13.02.2017.
 */
public class EntityLockerMemoryUsageTest {

    private static final int ENTITY_COUNT = 100;
    private static final int BYTES_IN_MB = 1024 * 1024;

    @Test
    public void ConcurrentHashMapEntityLockerTest() throws Exception {
        EntityLockerTest(new ConcurrentHashMapEntityLocker<>());
    }

    @Test
    public void WeakHashMapEntityLockerTest() throws Exception {
        EntityLockerTest(new WeakHashMapEntityLocker<>());
    }

    @Test
    public void LinkedHashMapEntityLockerTest() throws Exception {
        EntityLockerTest(new EntityLockerImpl<>(10));
    }

    private void EntityLockerTest(EntityLocker<Huge> entityLocker) throws Exception {
        System.out.printf("Free memory at start: %dmb%n", Runtime.getRuntime().freeMemory() / BYTES_IN_MB);
        Set<Huge> hugeSet = IntStream.range(0, ENTITY_COUNT).mapToObj(Huge::new).collect(Collectors.toSet());
        System.out.printf("Free memory before locking: %dmb%n", Runtime.getRuntime().freeMemory() / BYTES_IN_MB);
        for (Huge huge : hugeSet) {
            entityLocker.withLock(huge, () -> null);
        }
//        System.out.printf("Map size: %d%n", entityLocker.mapSize());
        hugeSet = null;
        for (int i = 0; i < 3; i++) {
            System.out.printf("Free memory after %d sec: %dmb%n", i, Runtime.getRuntime().freeMemory() / BYTES_IN_MB);
            System.gc();
            Thread.sleep(1000);
//            System.out.printf("Map size: %d%n", entityLocker.mapSize());
        }
    }

    private class Huge {
        int id;
        double[] payload = new double[1_000_000];

        public Huge(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Huge huge = (Huge) o;

            return id == huge.id;

        }

        @Override
        public int hashCode() {
            return id;
        }
    }
}
