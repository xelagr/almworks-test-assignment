package almworks;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Aleksei Grishkov on 14.02.2017.
 */
public final class EntityLockerImplDebug<T> implements EntityLocker<T> {

    private static final int DEFAULT_CACHE_SIZE = 50;

    private final Lock globalLock;
    private final Map<T, LockWrapper> lockMap;

    public EntityLockerImplDebug() {
        this(DEFAULT_CACHE_SIZE);
    }

    public EntityLockerImplDebug(int cacheSize) {
        if (cacheSize <= 0) {
            throw new IllegalArgumentException("Cache size should be > 0");
        }

        lockMap = new LinkedHashMap<T, LockWrapper>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<T, LockWrapper> eldest) {
                System.out.printf("In use: %b, size: %d%n", eldest.getValue().isInUse(), lockMap.size());
                try {
                    return withGlobalLock(() -> !eldest.getValue().isInUse() && lockMap.size() > cacheSize);
                } catch (Exception ignored) {}
                return false;
            }
        };
        globalLock = new ReentrantLock();
    }

    @Override
    public <V> V withLock(T entityId, Callable<V> callable) throws Exception {
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(callable);

        Lock lock = getLock(entityId);

        V result;
        lock.lock();
        try {
            result = callable.call();
        }
        finally {
            lock.unlock();
        }

        releaseLock(entityId);

        return result;
    }

    public void printLocks() {
        Iterator<LockWrapper> iterator = lockMap.values().iterator();
        for (int i = 0; i < lockMap.values().size(); i++) {
            System.out.printf("%d: %b%n", i, iterator.next().inUse);
        }
    }

    private Lock getLock(T entityId) throws Exception {
        return withGlobalLock(() -> {
            LockWrapper lw = lockMap.computeIfAbsent(entityId, o -> new LockWrapper());
            lw.setInUse(true);
            return lw.lock;
        });
    }

    private void releaseLock(T entityId) throws Exception {
        withGlobalLock(() -> {
            LockWrapper lw = lockMap.get(entityId);
            lw.setInUse(false);
        });
    }

    private <V> V withGlobalLock(Callable<V> callable) throws Exception {
        globalLock.lock();
        try {
            return callable.call();
        }
        finally {
            globalLock.unlock();
        }
    }

    private void withGlobalLock(Runnable callable) {
        globalLock.lock();
        try {
            callable.run();
        }
        finally {
            globalLock.unlock();
        }
    }

    private class LockWrapper {
        private final Lock lock;
        private volatile boolean inUse;

        LockWrapper() {
            this.lock = new ReentrantLock();
            this.inUse = false;
        }

        boolean isInUse() {
            return inUse;
        }

        void setInUse(boolean inUse) {
            this.inUse = inUse;
        }
    }


}
