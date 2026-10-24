package com.attendance.app;

/**
 * Plain launcher. A main class that does not extend Application keeps the
 * shaded / packaged jar runnable from the classpath without the classic
 * "JavaFX runtime components are missing" error.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}