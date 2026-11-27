package it.uniroma2.entity;

import java.util.Map;

/**
 * I risultati sul test di una configurazione (classificatore + ricetta) con una validazione, riassunti su tutte le
 * sue run.
 *
 * @param timesChosen   in quante split la ricetta è stata scelta sul validation per quel classificatore
 * @param timesChampion in quante split questa configurazione è stata il campione
 */
public record ConfigurationSummary(ValidationKind validation, ClassifierKind classifier, Recipe recipe,
                                   Map<Metric, MetricSummary> metrics, int timesChosen, int timesChampion) {
}