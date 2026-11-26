package it.uniroma2.entity;

/**
 * Numero di code smell di una classe in una release.
 *
 * @param nSmells null se SonarCloud non ha restituito il file (da controllare nel log)
 */
public record ClassSmells(int releaseId, String path, String classId, Integer nSmells) {
}