package it.uniroma2.entity;

/**
 * Il linkage tra ticket di Jira e commit di Git (Fase 10), nei due versi.
 *
 * @param bugTickets            ticket bug della Fase 2
 * @param ticketsWithCommits    ticket bug citati da almeno un commit (categorie A, B, C della Fase 7)
 * @param ticketsWithClasses    ticket bug i cui fix toccano almeno una classe del dataset
 * @param usedTickets           ticket bug non esclusi nella Fase 8 (hanno OV e FV)
 * @param usedWithCommits       ticket usati che hanno almeno un commit
 * @param commits               commit di tutti i rami, merge esclusi
 * @param commitsCitingBug      commit che citano un ticket bug (i commit di fix)
 * @param commitsCitingTicket   commit che citano un ticket qualsiasi del progetto (bug, feature, task…)
 */
public record Linkage(int bugTickets, int ticketsWithCommits, int ticketsWithClasses, int usedTickets,
                      int usedWithCommits, int commits, int commitsCitingBug, int commitsCitingTicket) {

    /** Percentuale di part su total (0 se total è 0). */
    public static double percent(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }
}