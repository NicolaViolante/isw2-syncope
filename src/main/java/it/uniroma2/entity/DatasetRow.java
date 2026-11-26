package it.uniroma2.entity;

import java.util.List;

/**
 * Una riga del dataset: una classe in una release, con le feature, NSmells e la bugginess.
 *
 * @param features i valori delle feature, nell'ordine dei nomi del {@link Dataset}
 */
public record DatasetRow(String project, int releaseId, String path, List<String> features, int nSmells,
                         boolean buggy) {

    public DatasetRow {
        features = List.copyOf(features);
    }

    /** La stessa riga con la bugginess indicata (usato dal labeling). */
    public DatasetRow withBuggy(boolean value) {
        return new DatasetRow(project, releaseId, path, features, nSmells, value);
    }
}