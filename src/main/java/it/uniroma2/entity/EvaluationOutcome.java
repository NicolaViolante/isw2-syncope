package it.uniroma2.entity;

import java.util.List;

/**
 * Il risultato della misura di un modello.
 *
 * @param features le colonne usate dal classificatore dopo la feature selection; vuota se non c'è feature selection
 *                 (cioè tutte le 21)
 */
public record EvaluationOutcome(ModelMetrics metrics, List<String> features) {
}