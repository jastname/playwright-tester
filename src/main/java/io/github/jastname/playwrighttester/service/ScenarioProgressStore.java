package io.github.jastname.playwrighttester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 시나리오 비동기 실행 진행률을 SSE 로 스트리밍하기 위한 저장소.
 * - 각 실행(Execution)은 UUID 로 식별됩니다.
 * - 이벤트를 버퍼에 쌓아두므로, SSE 연결 전에 발생한 이벤트도 재전송합니다.
 */
@Component
public class ScenarioProgressStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Execution> store = new ConcurrentHashMap<>();

    public Execution create() {
        String id = UUID.randomUUID().toString();
        Execution exec = new Execution(id);
        store.put(id, exec);
        return exec;
    }

    public Execution get(String id) {
        return store.get(id);
    }

    public void remove(String id) {
        store.remove(id);
    }

    // ─────────────────────────────────────────────────────────────────
    public static class Execution {

        public final String id;
        private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        /** 연결 전 이벤트도 재전송하기 위한 버퍼 */
        private final List<BufferedEvent> buffer = new CopyOnWriteArrayList<>();
        public volatile boolean done = false;

        Execution(String id) {
            this.id = id;
        }

        /**
         * SSE emitter 를 등록하고, 지금까지 버퍼에 쌓인 이벤트를 즉시 재전송합니다.
         */
        public void addEmitter(SseEmitter emitter) {
            // 버퍼 이벤트 먼저 재전송
            for (BufferedEvent be : buffer) {
                try {
                    emitter.send(SseEmitter.event().name(be.event).data(be.data));
                } catch (Exception ignored) {}
            }
            if (done) {
                try { emitter.complete(); } catch (Exception ignored) {}
                return;
            }
            emitters.add(emitter);
            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onTimeout(()    -> emitters.remove(emitter));
            emitter.onError(e       -> emitters.remove(emitter));
        }

        /**
         * 이벤트를 버퍼에 저장하고 연결된 모든 emitter 에 전송합니다.
         */
        public void emit(String event, Object payload) {
            String data;
            try {
                data = MAPPER.writeValueAsString(payload);
            } catch (Exception e) {
                data = payload.toString();
            }
            buffer.add(new BufferedEvent(event, data));
            for (SseEmitter em : emitters) {
                try {
                    em.send(SseEmitter.event().name(event).data(data));
                } catch (Exception ignored) {
                    emitters.remove(em);
                }
            }
        }

        /**
         * 스트림을 정상 종료합니다.
         */
        public void complete() {
            done = true;
            for (SseEmitter em : emitters) {
                try { em.complete(); } catch (Exception ignored) {}
            }
            emitters.clear();
        }

        private record BufferedEvent(String event, String data) {}
    }
}
