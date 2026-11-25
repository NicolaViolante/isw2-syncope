package it.uniroma2.entity;

/**
 * Età di una classe alla data di rilascio di Jira.
 *
 * @param age         settimane dal commit di creazione della classe
 * @param weightedAge età media dei commit, in settimane, pesata sulle righe toccate
 */
public record AgeMetrics(long age, double weightedAge) {
}