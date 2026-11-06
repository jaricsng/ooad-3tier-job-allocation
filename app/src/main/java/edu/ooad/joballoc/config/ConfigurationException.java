package edu.ooad.joballoc.config;

import java.util.List;

/** Thrown at start-up when one or more settings are invalid; lists every problem at once. */
public class ConfigurationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<String> problems;

    public ConfigurationException(List<String> problems) {
        super("Invalid configuration:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
