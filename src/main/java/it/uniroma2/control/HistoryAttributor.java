package it.uniroma2.control;

import it.uniroma2.entity.CommitData;
import it.uniroma2.entity.FileChange;
import it.uniroma2.entity.Touch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Attribuisce i commit della storia di una release alle classi presenti nel suo snapshot.
 *
 * <p>La storia viene percorsa all'indietro, dal tag verso il primo commit. Si parte dai percorsi delle
 * classi nello snapshot e, quando si incontra una rinomina, la classe viene seguita sul percorso
 * precedente. Una aggiunta (o copia) è la nascita della classe: prima di quel commit il percorso non
 * appartiene più a lei.
 */
public final class HistoryAttributor {

    private int renamesFollowed;

    /**
     * @param commits      la storia contenuta nel tag k, dal più recente al più vecchio (ordine topologico)
     * @param inRelease    gli id dei commit della finestra della release (nel tag k ma non nel tag k−1)
     * @param classesAtTag i percorsi delle classi nello snapshot
     * @return per ogni percorso nello snapshot, i commit che hanno toccato la classe (dal più recente)
     */
    public Map<String, List<Touch>> attribute(List<CommitData> commits, Set<String> inRelease,
                                              Set<String> classesAtTag) {
        renamesFollowed = 0;
        Map<String, String> tracked = new HashMap<>(); // percorso in quel punto della storia → percorso nello snapshot
        Map<String, List<Touch>> touches = new HashMap<>();
        for (String path : classesAtTag) {
            tracked.put(path, path);
            touches.put(path, new ArrayList<>());
        }
        for (CommitData commit : commits) {
            boolean window = inRelease.contains(commit.id());
            // 1) attribuzione con lo stato DOPO il commit
            for (FileChange change : commit.javaChanges()) {
                if (change.kind() == FileChange.Kind.DELETE) {
                    continue;
                }
                String owner = tracked.get(change.newPath());
                if (owner != null) {
                    touches.get(owner).add(new Touch(commit, change, window));
                }
            }
            // 2) passaggio allo stato PRIMA del commit
            Map<String, String> movedBack = new HashMap<>();
            for (FileChange change : commit.javaChanges()) {
                switch (change.kind()) {
                    case ADD, COPY -> tracked.remove(change.newPath());
                    case RENAME -> {
                        String owner = tracked.remove(change.newPath());
                        if (owner != null) {
                            movedBack.put(change.oldPath(), owner);
                        }
                    }
                    default -> {
                        // MODIFY e DELETE non cambiano il percorso
                    }
                }
            }
            for (Map.Entry<String, String> e : movedBack.entrySet()) {
                if (tracked.putIfAbsent(e.getKey(), e.getValue()) == null) {
                    renamesFollowed++;
                }
            }
        }
        return touches;
    }

    /** Quante rinomine sono state seguite nell'ultima chiamata (la storia di una release). */
    public int renamesFollowed() {
        return renamesFollowed;
    }
}