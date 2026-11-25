package it.uniroma2.entity;

import java.time.LocalDate;
import java.util.List;

/**
 * Un ticket di bug di Jira, con i soli dati che servono al calcolo delle versioni (Fase 4).
 *
 * @param key              chiave del ticket (es. "SYNCOPE-123")
 * @param created          giorno di creazione del ticket (da cui si ricava la OV)
 * @param resolved         giorno di risoluzione su Jira (da cui si ricava la FV)
 * @param affectedVersions Affected Version dichiarate su Jira (campo "versions")
 * @param fixVersions      Fix Version dichiarate su Jira (salvate per controllo, la FV si calcola dalla data)
 */
public record Ticket(String key, LocalDate created, LocalDate resolved,
                     List<JiraVersion> affectedVersions, List<JiraVersion> fixVersions) {

    public Ticket {
        affectedVersions = List.copyOf(affectedVersions);
        fixVersions = List.copyOf(fixVersions);
    }

    public boolean hasAffectedVersions() {
        return !affectedVersions.isEmpty();
    }

    /** Riferimento a una versione di Jira: ID (uguale alla colonna "Version ID" del CSV delle release) e nome. */
    public record JiraVersion(String id, String name) {
    }
}