package almworks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by Aleksei Grishkov on 08.02.2017.
 */
public class EntityLockerFactory {
    private static final ConcurrentMap<Class<?>, EntityLocker<?>> lockerMap = new ConcurrentHashMap<>();

    public static EntityLocker getEntityLocker(Class<?> clazz) {
        return lockerMap.computeIfAbsent(clazz, c -> new ConcurrentHashMapEntityLocker<>());
    }
}
