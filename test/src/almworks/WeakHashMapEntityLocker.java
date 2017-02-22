package almworks;

import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Aleksei Grishkov on 13.02.2017.
 */
public class WeakHashMapEntityLocker<T> implements EntityLocker<T> {
    private final WeakHashMap<T, Lock> lockMap = new WeakHashMap<>();

    @Override
    public <V> V withLock(T entityId, Callable<V> callable) throws Exception {
        Lock lock = lockMap.computeIfAbsent(entityId, e -> new ReentrantLock());
        lock.lock();
        try {
            return callable.call();
        } finally {
            lock.unlock();
        }
    }

}
