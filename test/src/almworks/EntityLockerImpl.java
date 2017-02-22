package almworks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Aleksei Grishkov on 14.02.2017.
 */
public final class EntityLockerImpl<T> implements EntityLocker<T> {

    private static final int DEFAULT_CACHE_SIZE = 100;

    private final Lock globalLock;
    private final Map<T, LockWrapper> lockMap;

    public EntityLockerImpl() {
        this(DEFAULT_CACHE_SIZE);
    }

    public EntityLockerImpl(int cacheSize) {
        if (cacheSize <= 0) {
            throw new IllegalArgumentException("Cache size should be > 0");
        }

        lockMap = new LinkedHashMap<T, LockWrapper>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<T, LockWrapper> eldest) {
                    return !eldest.getValue().isInUse() && this.size() > cacheSize;
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

    private Lock getLock(T entityId) throws Exception {
        return withGlobalLock(() -> {
            LockWrapper lw = lockMap.computeIfAbsent(entityId, o -> new LockWrapper());
            lw.incrementAcquires();
            return lw.lock;
        });
    }

    private void releaseLock(T entityId) throws Exception {
        withGlobalLock(() -> {
            LockWrapper lw = lockMap.get(entityId);
            lw.decrementAcquires();
            return null;
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

    int lockCount() {
        return lockMap.size();
    }

    private class LockWrapper {
        private final Lock lock;
        private int acquires;

        LockWrapper() {
            lock = new ReentrantLock();
            acquires = 0;
        }

        boolean isInUse() {
            return acquires > 0;
        }

        void incrementAcquires() {
            ++acquires;
        }

        void decrementAcquires() {
            --acquires;
        }
    }


}
