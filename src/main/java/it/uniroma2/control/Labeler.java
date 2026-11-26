package it.uniroma2.control;

import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.TicketVersions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fase 9: accende la bugginess (secondo tempo dell'algoritmo del prof).
 *
 * <p>Tutte le righe partono "no". Per ogni ticket non escluso nella Fase 8, le classi toccate dai suoi fix (Fase 7,
 * come Class ID) diventano "yes" in ogni release del dataset da IV inclusa a FV esclusa in cui la classe esiste. Il
 * Class ID fa sì che il bug segua la classe anche se nel frattempo è stata spostata. Si usano tutti i ticket fino a
 * oggi, anche quelli con FV oltre le release del dataset. Se più ticket segnano la stessa riga, resta "yes".
 */
public final class Labeler {

    private int contributingTickets;

    /**
     * @param dataset        il dataset della Fase 6 (tutte le righe a "no")
     * @param classIdsByRelease per ogni Release ID: percorso → Class ID (inventario della Fase 3)
     * @param fixes          le classi toccate da ogni ticket (Fase 7)
     * @param versions       OV, FV e IV di ogni ticket (Fase 8)
     */
    public Dataset label(Dataset dataset, Map<Integer, Map<String, String>> classIdsByRelease,
                         List<TicketFixes> fixes, List<TicketVersions> versions) {
        Map<String, TicketVersions> versionByKey = new HashMap<>();
        versions.forEach(v -> versionByKey.put(v.key(), v));

        Set<String> buggy = new HashSet<>(); // "releaseId|classId"
        contributingTickets = 0;
        for (TicketFixes f : fixes) {
            TicketVersions v = versionByKey.get(f.key());
            if (v == null || v.isExcluded() || f.classIds().isEmpty()) {
                continue;
            }
            boolean contributes = false;
            for (int release = v.iv(); release < v.fv(); release++) {
                Map<String, String> atRelease = classIdsByRelease.get(release);
                if (atRelease == null) {
                    continue; // release fuori dal dataset
                }
                Set<String> present = new HashSet<>(atRelease.values());
                for (String classId : f.classIds()) {
                    if (present.contains(classId)) {
                        buggy.add(release + "|" + classId);
                        contributes = true;
                    }
                }
            }
            if (contributes) {
                contributingTickets++;
            }
        }

        List<DatasetRow> rows = new ArrayList<>();
        for (DatasetRow row : dataset.rows()) {
            String classId = classIdsByRelease.get(row.releaseId()).get(row.path());
            if (classId == null) {
                throw new IllegalStateException("Classe assente dall'inventario: release " + row.releaseId() + ", "
                        + row.path());
            }
            rows.add(row.withBuggy(buggy.contains(row.releaseId() + "|" + classId)));
        }
        return new Dataset(dataset.featureNames(), rows);
    }

    /** Ticket che hanno segnato almeno una riga del dataset nell'ultima chiamata. */
    public int contributingTickets() {
        return contributingTickets;
    }
}
