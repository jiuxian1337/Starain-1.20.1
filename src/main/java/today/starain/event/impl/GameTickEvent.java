package today.starain.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import today.starain.event.api.Event;
import today.starain.event.api.EventType;

@Getter
@AllArgsConstructor
public class GameTickEvent implements Event {
    private EventType type;
}
