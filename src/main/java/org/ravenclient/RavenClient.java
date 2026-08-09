package org.ravenclient;

import javafx.application.Application;
import org.ravenclient.ui.LauncherUI;

public final class RavenClient {

    /**
     * Note: this class must NOT extend {@link javafx.application.Application}.
     * JavaFX apps that run from the classpath (rather than the module path) fail to
     * start when the main class extends Application, so we launch {@link LauncherUI}
     * explicitly instead.
     */
    public static void main(String[] args) {
        Application.launch(LauncherUI.class, args);
    }
}
