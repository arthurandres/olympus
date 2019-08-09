package org.aa.olympus.examples;

import com.google.common.collect.ImmutableSet;
import java.util.function.Consumer;
import org.aa.olympus.api.ElementHandle;
import org.aa.olympus.api.ElementManager;
import org.aa.olympus.api.ElementUpdater;
import org.aa.olympus.api.Engine;
import org.aa.olympus.api.EntityKey;
import org.aa.olympus.api.Event;
import org.aa.olympus.api.EventChannel;
import org.aa.olympus.api.Olympus;
import org.aa.olympus.api.Toolbox;
import org.aa.olympus.api.UpdateContext;
import org.aa.olympus.api.UpdateResult;

public class SimplestExample {

  static final EntityKey<String, Integer> COUNTER =
      Olympus.key("COUNTER", String.class, Integer.class);
  static final EventChannel<String> WORDS = Olympus.channel("WORDS", String.class);

  static final class CounterElementManager implements ElementManager<String, Integer> {

    @Override
    public ElementUpdater<Integer> create(
        String elementKey, UpdateContext updateContext, Toolbox toolbox) {
      return new ElementUpdater<Integer>() {
        int state = 0;

        @Override
        public UpdateResult<Integer> update(
            Integer previous, UpdateContext updateContext, Toolbox toolbox) {
          state += toolbox.getEvents().size();
          return UpdateResult.maybe(state);
        }

        @Override
        public <K2, S2> boolean onNewElement(ElementHandle<K2, S2> handle) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public <K2> void onNewKey(EntityKey<K2, ?> entityKey, K2 key, Consumer<String> toNotify) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <E> void onEvent(Event<E> event, Consumer<String> toNotify) {
      if (event.getChannel().equals(WORDS)) {
        toNotify.accept((String) event.getValue());
      }
    }
  }

  static Engine createEngine() {
    return Olympus.builder()
        .registerEventChannel(WORDS)
        .registerInnerEntity(
            COUNTER, new CounterElementManager(), ImmutableSet.of(), ImmutableSet.of(WORDS))
        .build();
  }
}
