package it.uniroma2.entity;

/**
 * Le feature di una riga del dataset (una classe in una release), senza NSmells e Bugginess.
 *
 * @param code      metriche di codice; null se JavaParser non è riuscito a leggere il file
 * @param inRelease metriche di processo sui commit della release
 * @param history   metriche di processo su tutti i commit contenuti nel tag ("dall'inizio")
 */
public record ClassMetrics(int releaseId, String path, String classId, CodeMetrics code,
                           ProcessMetrics inRelease, ProcessMetrics history, AgeMetrics age) {
}