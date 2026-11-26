package it.uniroma2.control;

import it.uniroma2.entity.CommitData;
import it.uniroma2.entity.FileChange;
import it.uniroma2.entity.TicketFixes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fase 7: collega i ticket bug ai loro commit di fix e alle classi del dataset che toccano.
 *
 * <ul>
 *   <li>un commit è un fix dei ticket bug citati nel messaggio ({@link FixCommitFinder}: chiave esatta, revert puri
 *       esclusi); un commit che cita più ticket conta per tutti, un ticket con più commit li somma tutti;</li>
 *   <li>categoria: A se ha fix raggiungibili da master e fix solo su altri rami, B solo master, C solo rami,
 *       D nessun fix;</li>
 *   <li>classi: unione dei file .java di produzione toccati da tutti i fix (master e rami), ricondotti al Class ID
 *       dell'inventario. Dopo la riorganizzazione della 2.0 il fix su master tocca percorsi che nel dataset non
 *       esistono, mentre il backport sul ramo tocca le classi delle nostre release: per questo il backport non si
 *       ignora. Se master e backport toccano la stessa classe, il Class ID la conta una volta.</li>
 * </ul>
 */
public final class FixLinker {

    private FixLinker() {
    }

    /** Per ogni ticket bug citato, gli id dei commit che lo correggono (nell'ordine dei messaggi ricevuti). */
    public static Map<String, List<String>> commitsByTicket(Map<String, String> messages, FixCommitFinder finder) {
        Map<String, List<String>> byTicket = new LinkedHashMap<>();
        messages.forEach((id, message) -> {
            for (String key : finder.citedBugs(message)) {
                byTicket.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
            }
        });
        return byTicket;
    }

    /**
     * @param ticketKeys      tutti i ticket bug (anche quelli senza commit, che diventano D)
     * @param commitsByTicket risultato di {@link #commitsByTicket}
     * @param onMaster        id dei commit raggiungibili da master
     * @param commits         dati (diff) dei commit di fix
     * @param classIdByPath   percorso → Class ID, dall'inventario di tutte le release del dataset
     */
    public static List<TicketFixes> link(Collection<String> ticketKeys, Map<String, List<String>> commitsByTicket,
                                         Set<String> onMaster, Map<String, CommitData> commits,
                                         Map<String, String> classIdByPath) {
        List<TicketFixes> result = new ArrayList<>();
        for (String key : new TreeSet<>(ticketKeys)) {
            List<String> master = new ArrayList<>();
            List<String> branch = new ArrayList<>();
            for (String id : commitsByTicket.getOrDefault(key, List.of())) {
                (onMaster.contains(id) ? master : branch).add(id);
            }
            Set<String> classIds = new HashSet<>();
            Set<String> other = new HashSet<>();
            for (String id : concat(master, branch)) {
                for (String path : productionPaths(commits.get(id))) {
                    String classId = classIdByPath.get(path);
                    if (classId != null) {
                        classIds.add(classId);
                    } else {
                        other.add(path);
                    }
                }
            }
            result.add(new TicketFixes(key, category(master, branch), master, branch, classIds, other.size()));
        }
        return result;
    }

    private static TicketFixes.Category category(List<String> master, List<String> branch) {
        if (!master.isEmpty()) {
            return branch.isEmpty() ? TicketFixes.Category.B : TicketFixes.Category.A;
        }
        return branch.isEmpty() ? TicketFixes.Category.D : TicketFixes.Category.C;
    }

    /** I file .java di produzione toccati dal commit: percorso nuovo e, per rinomine e cancellazioni, quello vecchio. */
    private static Set<String> productionPaths(CommitData commit) {
        Set<String> paths = new HashSet<>();
        for (FileChange change : commit.javaChanges()) {
            for (String path : new String[] {change.oldPath(), change.newPath()}) {
                if (path != null && ProductionFiles.isProduction(path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}