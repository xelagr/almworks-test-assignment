package almworks;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Aleksei Grishkov on 22.01.2017.
 */
public class ConcurrentHashMapEntityLocker<T> implements EntityLocker<T> {
    private final ConcurrentMap<T, Lock> lockMap = new ConcurrentHashMap<>();

    @Override
    public <V> V withLock(T entityId, Callable<V> callable) throws Exception {
        Objects.requireNonNull(entityId);
        Lock lock = lockMap.computeIfAbsent(entityId, o -> new ReentrantLock());
        lock.lock();
        try {
            return callable.call();
        }
        finally {
            lock.unlock();
        }
    }


    /*public <V> V tryCallWithLock(T entityId, Callable<V> callable, long time, TimeUnit unit) throws Exception {
        Objects.requireNonNull(entityId);
        Lock lock = lockMap.computeIfAbsent(entityId, o -> new ReentrantLock());
        if (lock.tryLock(time, unit)) {
            try {
                return callable.call();
            } finally {
                lock.unlock();
            }
        }
        throw new TimeoutException();
    }*/

}
