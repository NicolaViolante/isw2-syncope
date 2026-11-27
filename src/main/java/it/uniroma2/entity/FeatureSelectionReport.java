package it.uniroma2.entity;

import java.util.List;
import java.util.Map;

/**
 * Il risultato di una strada di feature selection sul training dell'Ordered Holdout.
 *
 * @param method  la strada (valutatore e ricerca)
 * @param kept    le feature tenute, nell'ordine del dataset (per un ranking: dalla più informativa)
 * @param dropped le feature scartate, nell'ordine del dataset
 * @param scores  per un ranking, il punteggio di ogni feature (dalla più informativa); vuota per le altre strade
 * @param seconds tempo impiegato
 */
public record FeatureSelectionReport(String method, List<String> kept, List<String> dropped,
                                     Map<String, Double> scores, double seconds) {
}