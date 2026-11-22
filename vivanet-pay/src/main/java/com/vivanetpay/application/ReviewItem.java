package com.vivanetpay.application;

import com.vivanetpay.domain.Validation;
import com.vivanetpay.reporting.HistoryRow;

import java.util.List;

/**
 * Elemento de la bandeja de revisión (RF-023, Flujo D paso 27): datos extraídos, texto de origen permitido,
 * motivo de incertidumbre y validaciones.
 *
 * @param row         datos del evento/movimiento
 * @param rawSubject  asunto/título original
 * @param rawText     texto original permitido
 * @param validations evidencias registradas
 */
public record ReviewItem(HistoryRow row, String rawSubject, String rawText, List<Validation> validations) {
    public ReviewItem {
        validations = List.copyOf(validations);
    }
}
