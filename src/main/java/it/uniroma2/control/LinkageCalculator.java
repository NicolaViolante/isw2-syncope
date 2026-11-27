package it.uniroma2.control;

import it.uniroma2.entity.Linkage;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.TicketVersions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 10: il linkage richiesto dal prof, nei due versi.
 *
 * <ul>
 *   <li>ticket → commit: quanti ticket bug hanno almeno un commit di fix (categorie A, B, C della Fase 7), su tutti
 *       e sui soli ticket usati per il labeling (non esclusi nella Fase 8);</li>
 *   <li>commit → ticket: quanti commit (tutti i rami, merge esclusi) citano un ticket bug e quanti un ticket
 *       qualsiasi del progetto.</li>
 * </ul>
 */
public final class LinkageCalculator {

    private LinkageCalculator() {
    }

    /**
     * @param fixes    i ticket bug con i loro commit di fix (Fase 7)
     * @param versions OV, FV e IV dei ticket (Fase 8)
     * @param messages i messaggi di tutti i commit, merge esclusi (id → messaggio)
     * @param finder   la stessa regola di riconoscimento dei fix usata nelle Fasi 4 e 7
     */
    public static Linkage compute(List<TicketFixes> fixes, List<TicketVersions> versions,
                                  Map<String, String> messages, FixCommitFinder finder) {
        Map<String, TicketVersions> versionByKey = new HashMap<>();
        versions.forEach(v -> versionByKey.put(v.key(), v));
        int withCommits = 0;
        int withClasses = 0;
        int used = 0;
        int usedWithCommits = 0;
        for (TicketFixes f : fixes) {
            boolean linked = f.category() != TicketFixes.Category.D;
            if (linked) {
                withCommits++;
            }
            if (!f.classIds().isEmpty()) {
                withClasses++;
            }
            TicketVersions v = versionByKey.get(f.key());
            if (v != null && !v.isExcluded()) {
                used++;
                if (linked) {
                    usedWithCommits++;
                }
            }
        }
        int citingBug = 0;
        int citingTicket = 0;
        for (String message : messages.values()) {
            if (finder.isFix(message)) {
                citingBug++;
            }
            if (finder.citesTicket(message)) {
                citingTicket++;
            }
        }
        return new Linkage(fixes.size(), withCommits, withClasses, used, usedWithCommits, messages.size(),
                citingBug, citingTicket);
    }
}