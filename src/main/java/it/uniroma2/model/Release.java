package it.uniroma2.model;

import java.time.LocalDate;

/**
 * Una versione di Jira del progetto.
 *
 * @param jiraId     ID della versione su Jira
 * @param name       nome della versione su Jira (es. "1.0.0-incubating")
 * @param date       data di rilascio, null se la versione non è stata rilasciata
 * @param major      prima cifra della versione (es. 1 per "1.2.3")
 * @param minor      seconda cifra della versione (es. 2 per "1.2.3"): major.minor identifica la linea
 * @param seqId      ID progressivo nella linea principale (1, 2, 3…), 0 se la release è scartata
 * @param discardReason motivo dello scarto, null se la release è tenuta
 */
public record Release(String jiraId, String name, LocalDate date, int major, int minor,
                      int seqId, String discardReason) {

    public boolean isKept() {
        return discardReason == null;
    }

    /** Confronta la linea (major.minor) di questa release con quella di un'altra. */
    public int compareLineTo(Release other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        return Integer.compare(minor, other.minor);
    }

    public String line() {
        return major + "." + minor;
    }

    public Release withSeqId(int newSeqId) {
        return new Release(jiraId, name, date, major, minor, newSeqId, null);
    }

    public Release discarded(String reason) {
        return new Release(jiraId, name, date, major, minor, 0, reason);
    }
}
