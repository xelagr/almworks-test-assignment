package almworks;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Created by Aleksei Grishkov on 08.02.2017.
 */
public interface EntityLocker<T> {
    <V> V withLock(T entityId, Callable<V> callable) throws Exception;
}
