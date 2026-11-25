package it.uniroma2.entity;

import java.time.LocalDate;

/**
 * Il codice di una release così come è stato rilasciato.
 *
 * @param release    la release della linea principale
 * @param reference  da dove è stato preso: il tag (es. "syncope-1.2.6") o, se manca, "master"
 * @param commitId   hash del commit dello snapshot
 * @param commitDate giorno del commit
 * @param fromTag    true se lo snapshot viene dal tag, false se dal ripiego sull'ultimo commit di master
 */
public record Snapshot(Release release, String reference, String commitId, LocalDate commitDate, boolean fromTag) {
}