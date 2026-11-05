package org.readybell.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.readybell.shared.domain.DomainEvent;

/** Domain event &lt;-&gt; JSON. Only {@link DomainEvent} classes of this application can be read back. */
public class OutboxEventSerializer {

    private final ObjectMapper mapper;

    public OutboxEventSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(DomainEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + event.getClass().getName(), e);
        }
    }

    public DomainEvent fromJson(String type, String json) {
        try {
            Class<?> clazz = Class.forName(type);
            if (!DomainEvent.class.isAssignableFrom(clazz) || !clazz.getName().startsWith("org.readybell.")) {
                throw new IllegalArgumentException("Not a domain event type: " + type);
            }
            return (DomainEvent) mapper.readValue(json, clazz);
        } catch (ClassNotFoundException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot read outbox event of type " + type, e);
        }
    }
}
