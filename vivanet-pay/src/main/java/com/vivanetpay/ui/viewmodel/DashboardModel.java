package com.vivanetpay.ui.viewmodel;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.function.Function;

/**
 * Estado observable del Tablero. La pantalla solo enlaza (bind) sus controles a estas propiedades; para refrescar
 * basta con {@link #update(DashboardViewModel)} (siempre en el hilo de JavaFX). No necesita pantalla ni toolkit
 * gráfico: solo usa {@code javafx.base}, por lo que se prueba sin display.
 */
public final class DashboardModel {
    private final ObjectProperty<DashboardViewModel> current = new SimpleObjectProperty<>();
    private final ObservableList<DashboardViewModel.BankLine> banks = FXCollections.observableArrayList();
    private final ObservableList<DashboardViewModel.ConnectorLine> connectors = FXCollections.observableArrayList();

    public void update(DashboardViewModel vm) {
        current.set(vm);
        banks.setAll(vm.banks());
        connectors.setAll(vm.connectors());
    }

    public ObservableList<DashboardViewModel.BankLine> banks() {
        return banks;
    }

    public ObservableList<DashboardViewModel.ConnectorLine> connectors() {
        return connectors;
    }

    /** Texto derivado del modelo actual; {@code ifEmpty} mientras no hay datos cargados. */
    public StringBinding text(Function<DashboardViewModel, String> f, String ifEmpty) {
        return Bindings.createStringBinding(() -> {
            DashboardViewModel vm = current.get();
            return vm == null ? ifEmpty : f.apply(vm);
        }, current);
    }

    public StringBinding day() {
        return text(vm -> "Hoy " + vm.dayText() + " (hora de Colombia)", "");
    }

    public BooleanBinding hasPending() {
        ObservableValue<DashboardViewModel> c = current;
        return Bindings.createBooleanBinding(() -> c.getValue() != null && c.getValue().hasPending(), current);
    }

    public ObjectProperty<DashboardViewModel> currentProperty() {
        return current;
    }
}
