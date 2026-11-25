package it.uniroma2.entity;

/** Le metriche di processo di una classe su un insieme di commit (nella release o dall'inizio). */
public record ProcessMetrics(int nr, int nFix, int nAuth, int locAdded, int maxLocAdded,
                             int churn, int maxChurn, int changeSetSize, int maxChangeSet) {

    /** Nessun commit: tutte le metriche valgono 0. */
    public static final ProcessMetrics EMPTY = new ProcessMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
}