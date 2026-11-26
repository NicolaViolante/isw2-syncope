package it.uniroma2.entity;

import java.util.List;

/** Il dataset: i nomi delle feature (nell'ordine delle colonne) e le righe. */
public record Dataset(List<String> featureNames, List<DatasetRow> rows) {

    public Dataset {
        featureNames = List.copyOf(featureNames);
        rows = List.copyOf(rows);
    }
}
