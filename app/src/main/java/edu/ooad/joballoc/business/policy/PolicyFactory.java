package edu.ooad.joballoc.business.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FACTORY: returns the {@link AllocationPolicy} for the name chosen on screen.
 * Register a new policy here; the service and UI pick it up automatically.
 *
 * <p>Refactored: policies are stateless, so the registry holds one shared instance of each
 * (FLYWEIGHT-style sharing) instead of a {@code Supplier} that created a new object on every
 * call. Registration also rejects duplicate names, which used to replace a policy silently.
 */
public class PolicyFactory {

    public static final String DEFAULT = "SKILL_MATCH";

    private final Map<String, AllocationPolicy> registry = new LinkedHashMap<>();

    public PolicyFactory() {
        register(new SkillMatchPolicy());
        register(new LeastLoadedPolicy());
        register(new FirstFitPolicy());
    }

    public final void register(AllocationPolicy policy) {
        if (registry.putIfAbsent(policy.name(), policy) != null) {
            throw new IllegalArgumentException("A policy named " + policy.name() + " is already registered");
        }
    }

    public AllocationPolicy forName(String name) {
        String key = name == null || name.isBlank() ? DEFAULT : name.trim().toUpperCase();
        AllocationPolicy policy = registry.get(key);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown allocation policy '" + name + "'. Known: " + names());
        }
        return policy;
    }

    public List<String> names() {
        return List.copyOf(registry.keySet());
    }
}
