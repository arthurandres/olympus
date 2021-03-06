package org.aa.olympus.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.aa.olympus.api.ElementManager;
import org.aa.olympus.api.Engine;
import org.aa.olympus.api.EngineBuilder;
import org.aa.olympus.api.EntityKey;
import org.aa.olympus.api.EventChannel;
import org.aa.olympus.api.SimpleElementManager;

public final class EngineBuilderImpl implements EngineBuilder {

  Set<EventChannel> eventChannels = new HashSet<>();
  Map<EntityKey, EntityUnit> entities = new HashMap<>();

  private static void checkNoDuplicate(Collection<EntityKey> keys, String name) {
    Preconditions.checkArgument(keys.stream().map(EntityKey::getName).noneMatch(name::equals));
  }

  private static void checkNoDuplicateChannel(Collection<EventChannel> channels, String name) {
    Preconditions.checkArgument(
        channels.stream().map(EventChannel::getName).noneMatch(name::equals));
  }

  @Override
  public <E> EngineBuilder registerEventChannel(EventChannel<E> key) {
    checkNoDuplicateChannel(eventChannels, key.getName());
    eventChannels.add(key);
    return this;
  }

  @Override
  public <K, S> EngineBuilderImpl registerInnerEntity(
      EntityKey<K, S> key,
      ElementManager<K, S> manager,
      Set<EntityKey> dependencies,
      Set<EventChannel> channels) {
    Preconditions.checkArgument(
        !channels.isEmpty() || !dependencies.isEmpty(), "Entities must have dependencies");
    checkKey(key);
    checkDependencies(key, dependencies);
    checkChannels(key, channels);
    entities.put(key, new EntityUnit<>(key, manager, dependencies, channels));
    return this;
  }

  @Override
  public <K, S> EngineBuilder registerSimpleEntity(
      EntityKey<K, S> key, SimpleElementManager<K, S> manager, Set<EntityKey<K, ?>> dependencies) {
    for (EntityKey<K, ?> dependency : dependencies) {
      Preconditions.checkArgument(
          key.getKeyType().equals(dependency.getKeyType()),
          "%s dependencies must have the same key type. %s vs %s",
          SimpleElementManager.class.getSimpleName(),
          key.getKeyType(),
          dependency.getKeyType());
    }
    return registerInnerEntity(
        key,
        new SimpleElementManagerAdapter<>(manager),
        new HashSet<>(dependencies),
        ImmutableSet.of());
  }

  @Override
  public Engine build() {
    return new EngineAssembler(this).assemble();
  }

  /** Check key doesn't exists already */
  private void checkKey(EntityKey key) {
    checkNoDuplicate(entities.keySet(), key.getName());
  }

  /** Gets the dependencies of an entity, or null if it doesn't exists */
  private boolean exists(EntityKey entityKey) {
    return entities.containsKey(entityKey);
  }

  private void checkDependencies(EntityKey key, Set<EntityKey> dependencies) {
    Set<String> missing = new TreeSet<>();
    for (EntityKey dependency : dependencies) {
      if (!exists(dependency)) {
        missing.add(dependency.getName());
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Missing dependencies for %s: %s", key.getName(), missing));
    }
  }

  private void checkChannels(EntityKey key, Set<EventChannel> channels) {
    Set<String> missing = new TreeSet<>();
    for (EventChannel channel : channels) {
      if (!this.eventChannels.contains(channel)) {
        missing.add(channel.getName());
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Missing channels for %s: %s", key.getName(), missing));
    }
  }

  static final class SourceUnit<K, S> {
    final EntityKey<K, S> key;

    SourceUnit(EntityKey<K, S> key) {
      this.key = key;
    }
  }

  static final class EntityUnit<K, S> {
    final EntityKey<K, S> entityKey;
    final ElementManager<K, S> elementManager;
    final Set<EntityKey> dependencies;
    final Set<EventChannel> channels;

    EntityUnit(
        EntityKey<K, S> entityKey,
        ElementManager<K, S> elementManager,
        Set<EntityKey> dependencies,
        Set<EventChannel> channels) {
      this.entityKey = entityKey;
      this.elementManager = elementManager;
      this.dependencies = ImmutableSet.copyOf(dependencies);
      this.channels = ImmutableSet.copyOf(channels);
    }

    EntityManager<K, S> createManager(
        EngineContext engineContext,
        TimerStore timerStore,
        Map<EntityKey, EntityManager> dependencies,
        Set<EntityKey> dependents) {
      Preconditions.checkArgument(dependencies.keySet().equals(this.dependencies));
      return new EntityManager<>(
          engineContext, timerStore, entityKey, elementManager, dependencies, dependents, channels);
    }

    public EntityKey<K, S> getEntityKey() {
      return entityKey;
    }

    public ElementManager<K, S> getElementManager() {
      return elementManager;
    }

    Set<EntityKey> getDependencies() {
      return dependencies;
    }
  }

  @Override
  public <K, S> EngineBuilder registerInnerEntity(
      EntityKey<K, S> key, ElementManager<K, S> manager, Set<EntityKey> dependencies) {
    return registerInnerEntity(key, manager, dependencies, ImmutableSet.of());
  }

  @Override
  public <E, K, S> EngineBuilder eventToEntity(
      EventChannel<E> eventChannel,
      EntityKey<K, S> entityKey,
      Function<E, K> keyExtractor,
      Function<E, S> stateExtractor) {
    return registerInnerEntity(
        entityKey,
        new ChannelElementManager<>(eventChannel, keyExtractor, stateExtractor),
        ImmutableSet.of(),
        ImmutableSet.of(eventChannel));
  }

  @Override
  public EngineBuilder pipe(Function<EngineBuilder, EngineBuilder> transformer) {
    return transformer.apply(this);
  }
}
