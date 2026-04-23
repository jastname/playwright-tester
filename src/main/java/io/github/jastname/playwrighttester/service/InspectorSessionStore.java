package io.github.jastname.playwrighttester.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * 브라우저 UI 인스펙터 세션을 메모리에 보관합니다.
 */
@Component
public class InspectorSessionStore {

    public static class Session {
        public final String id;
        public volatile boolean running = false;
        public volatile String errorMessage = null;
        public final List<Map<String, Object>> elements = new CopyOnWriteArrayList<>();
        public final CountDownLatch stopSignal = new CountDownLatch(1);
        public volatile Thread browserThread;

        public Session(String id) {
            this.id = id;
        }
    }

    private final Map<String, Session> map = new ConcurrentHashMap<>();

    public Session create() {
        Session s = new Session(UUID.randomUUID().toString());
        map.put(s.id, s);
        return s;
    }

    public Session get(String id) {
        return map.get(id);
    }

    public void remove(String id) {
        map.remove(id);
    }
}
