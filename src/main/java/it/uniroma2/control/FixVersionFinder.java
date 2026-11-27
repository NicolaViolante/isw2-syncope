package it.uniroma2.control;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fase 8: la Fix Version presa dal codice.
 *
 * <p>FV = la prima release della linea principale, successiva all'OV, il cui tag contiene almeno un commit di fix del
 * ticket: è "la prima versione del codice senza quel bug" (definizione del prof). Non si usa la data di risoluzione
 * su Jira, perché la linea principale alterna rami: una release del ramo vecchio uscita dopo il fix su master non lo
 * contiene, e per quella release il bug c'è ancora. È la stessa unità delle altre scelte sul codice: finestre dai tag
 * (Fase 4), categorie dai rami che contengono il commit (Fase 7), AV ricondotte per discendenza (Fase 8).
 */
public final class FixVersionFinder {

    private final TreeMap<Integer, Set<String>> fixCommitsByRelease;

    /**
     * @param fixCommitsByRelease per ogni release della linea principale (Release ID), i commit di fix contenuti nel
     *                            suo tag
     */
    public FixVersionFinder(Map<Integer, Set<String>> fixCommitsByRelease) {
        this.fixCommitsByRelease = new TreeMap<>(fixCommitsByRelease);
    }

    /**
     * @param after      l'OV del ticket (0 se il ticket non ha OV): la FV è cercata nelle release successive
     * @param fixCommits i commit di fix del ticket (Fase 7), su master e sui rami
     * @return la FV, oppure null se nessuna release successiva contiene un commit di fix (ticket senza commit, fix
     *         solo su rami scartati o non ancora rilasciato)
     */
    public Integer fixVersion(int after, Collection<String> fixCommits) {
        if (fixCommits.isEmpty()) {
            return null;
        }
        for (Map.Entry<Integer, Set<String>> e : fixCommitsByRelease.tailMap(after, false).entrySet()) {
            for (String commit : fixCommits) {
                if (e.getValue().contains(commit)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }
}