package org.readybell.testsupport;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Emitter that records what would have been written to the client. */
public class RecordingEmitter extends SseEmitter {

    private final List<String> chunks = new CopyOnWriteArrayList<>();

    public RecordingEmitter() {
        super(0L);
    }

    @Override
    public synchronized void send(SseEventBuilder builder) {
        builder.build().forEach(d -> chunks.add(String.valueOf(d.getData())));
    }

    /** Everything written so far, concatenated (Spring splits one event into several chunks). */
    public String text() {
        return String.join("", chunks);
    }
}
