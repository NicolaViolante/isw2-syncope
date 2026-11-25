package it.uniroma2.entity;

import java.time.LocalDate;
import java.util.List;

/**
 * I dati di un commit che servono alle metriche di processo.
 *
 * @param filesChanged numero di file di qualsiasi tipo modificati nel commit (change set)
 * @param javaChanges  le modifiche ai soli file .java
 */
public record CommitData(String id, String authorEmail, LocalDate date, String message,
                         int filesChanged, List<FileChange> javaChanges) {

    public CommitData {
        javaChanges = List.copyOf(javaChanges);
    }
}