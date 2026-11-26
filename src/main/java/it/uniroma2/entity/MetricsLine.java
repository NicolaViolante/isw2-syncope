package it.uniroma2.entity;

import java.util.List;

/** Una riga del file delle metriche (Fase 4): chiave (Release ID, Class) e valori delle feature, come sono scritti. */
public record MetricsLine(int releaseId, String path, List<String> values) {

    public MetricsLine {
        values = List.copyOf(values);
    }
}