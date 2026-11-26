package it.uniroma2.control;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Riconosce i commit di fix: quelli il cui messaggio cita almeno uno dei ticket bug della Fase 2.
 *
 * <p>Regola unica, usata per NFix (Fase 4) e per il linkage dei fix (Fase 7):
 * <ul>
 *   <li>la chiave è riconosciuta esattamente (SYNCOPE-1 non corrisponde a SYNCOPE-10), senza distinguere
 *       maiuscole e minuscole;</li>
 *   <li>un commit che cita un bug e un ticket di altro tipo conta come fix;</li>
 *   <li>i revert puri (titolo che inizia con "Revert", come quelli generati da git revert) non sono fix: annullano
 *       un fix invece di farlo. Un commit con un titolo proprio che cita il ticket resta un fix anche se nel corpo
 *       dice di annullare una modifica precedente (es. "[SYNCOPE-1763] Back to CXF 3.5.6 as temporary
 *       workaround").</li>
 * </ul>
 */
public final class FixCommitFinder {

    private final String prefix;
    private final Pattern keyPattern;
    private final Set<String> bugKeys;

    /**
     * @param project nome del progetto su Jira (es. SYNCOPE)
     * @param bugKeys le chiavi dei ticket bug (Bug, Resolved/Closed, Fixed)
     */
    public FixCommitFinder(String project, Set<String> bugKeys) {
        this.prefix = project.toUpperCase(Locale.ROOT) + "-";
        // nessuna lettera o cifra prima della chiave, nessuna cifra dopo il numero
        this.keyPattern = Pattern.compile("(?i)(?<![A-Za-z0-9])" + Pattern.quote(project) + "-(\\d+)(?!\\d)");
        this.bugKeys = Set.copyOf(bugKeys);
    }

    /** I ticket bug citati dal messaggio, nell'ordine in cui compaiono; vuoto per i revert. */
    public Set<String> citedBugs(String message) {
        if (message == null || isRevert(message)) {
            return Collections.emptySet();
        }
        Set<String> cited = new LinkedHashSet<>();
        Matcher m = keyPattern.matcher(message);
        while (m.find()) {
            String key = prefix + Integer.parseInt(m.group(1));
            if (bugKeys.contains(key)) {
                cited.add(key);
            }
        }
        return cited;
    }

    public boolean isFix(String message) {
        return !citedBugs(message).isEmpty();
    }

    /** Un revert puro: il titolo (prima riga) del messaggio inizia con "Revert". */
    static boolean isRevert(String message) {
        return message.strip().toLowerCase(Locale.ROOT).startsWith("revert");
    }
}