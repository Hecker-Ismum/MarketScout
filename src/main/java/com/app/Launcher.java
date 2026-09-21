package com.app;

/**
 * Launcher class that works around the JavaFX module-path restriction.
 *
 * When running JavaFX without the module system (i.e. on the classpath),
 * the JVM checks whether the main class extends {@code Application}.
 * If it does, the JVM requires JavaFX modules on the module-path and
 * throws "JavaFX runtime components are missing" when they aren't.
 *
 * By using a plain launcher class that does NOT extend Application,
 * the JVM skips that check and JavaFX boots normally via the classpath.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
