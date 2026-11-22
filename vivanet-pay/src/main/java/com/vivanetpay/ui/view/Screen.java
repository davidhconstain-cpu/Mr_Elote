package com.vivanetpay.ui.view;

import javafx.scene.Node;

/** Pantalla de la navegación lateral. */
public interface Screen {
    String id();

    String title();

    Node node();

    /** Recarga datos (la implementación lee la base en segundo plano). Se llama en el hilo de JavaFX. */
    void refresh();
}
