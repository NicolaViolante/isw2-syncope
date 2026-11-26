package it.uniroma2.control;

import it.uniroma2.entity.ClassSmells;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.MetricsLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unisce le feature della Fase 4 e gli smell della Fase 5 in un'unica tabella, con Bugginess = "no" per tutte le
 * righe (primo tempo dell'algoritmo del prof).
 *
 * <p>L'unione avviene sulla coppia (Release ID, Class). Il dataset viene costruito solo se le due tabelle
 * contengono esattamente le stesse coppie e nessun valore manca: altrimenti si ferma con un errore, invece di
 * produrre righe incomplete.
 */
public final class DatasetBuilder {

    private DatasetBuilder() {
    }

    /**
     * @param project      nome del progetto (prima colonna)
     * @param featureNames i nomi delle feature, nell'ordine in cui compaiono in ogni riga di metrics
     * @param metrics      per ogni riga della Fase 4: Release ID, Class e i valori delle feature
     * @param smells       le righe della Fase 5
     */
    public static Dataset build(String project, List<String> featureNames, List<MetricsLine> metrics,
                                List<ClassSmells> smells) {
        Map<String, ClassSmells> smellsByKey = new HashMap<>();
        for (ClassSmells s : smells) {
            if (s.nSmells() == null) {
                throw new IllegalStateException("NSmells mancante: release " + s.releaseId() + ", " + s.path());
            }
            if (smellsByKey.put(key(s.releaseId(), s.path()), s) != null) {
                throw new IllegalStateException("Riga di smell duplicata: release " + s.releaseId() + ", " + s.path());
            }
        }
        if (smellsByKey.size() != metrics.size()) {
            throw new IllegalStateException("Righe diverse: " + metrics.size() + " con le metriche, "
                    + smellsByKey.size() + " con gli smell");
        }
        List<DatasetRow> rows = new ArrayList<>();
        for (MetricsLine m : metrics) {
            if (m.values().size() != featureNames.size() || m.values().stream().anyMatch(String::isEmpty)) {
                throw new IllegalStateException("Feature mancanti: release " + m.releaseId() + ", " + m.path());
            }
            ClassSmells s = smellsByKey.get(key(m.releaseId(), m.path()));
            if (s == null) {
                throw new IllegalStateException("Smell mancanti: release " + m.releaseId() + ", " + m.path());
            }
            rows.add(new DatasetRow(project, m.releaseId(), m.path(), m.values(), s.nSmells(), false));
        }
        rows.sort(Comparator.comparingInt(DatasetRow::releaseId).thenComparing(DatasetRow::path));
        return new Dataset(featureNames, rows);
    }

    private static String key(int releaseId, String path) {
        return releaseId + "|" + path;
    }
}