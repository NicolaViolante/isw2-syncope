package it.uniroma2.control;

import it.uniroma2.entity.ReleaseClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Costruisce l'inventario delle classi di produzione per ogni release (Fase 3), assegnando a ogni
 * classe un'identità stabile che sopravvive a spostamenti e rinomine. Dai file ricevuti tiene solo
 * quelli di produzione ({@link ProductionFiles}).
 *
 * <p>Regola: alla prima release l'identità di una classe è il suo percorso. In ogni release
 * successiva una classe conserva l'identità che aveva nella release precedente se ha lo stesso
 * percorso, oppure se Git ha riconosciuto che è stata rinominata o spostata da un altro percorso.
 * Altrimenti è una classe nuova e la sua identità è il percorso attuale.
 */
public final class ClassInventoryBuilder {

    private int renamesFollowed;

    /**
     * @param releaseIds     ID delle release, in ordine (1, 2, 3…)
     * @param filesByRelease per ogni release, i percorsi dei file .java dello snapshot
     * @param renamesInto    per ogni release k, le rinomine rilevate tra la release k-1 e k
     *                       (percorso vecchio → percorso nuovo); vuota per la prima release
     */
    public List<ReleaseClass> build(List<Integer> releaseIds,
                                    Map<Integer, List<String>> filesByRelease,
                                    Map<Integer, Map<String, String>> renamesInto) {
        List<ReleaseClass> inventory = new ArrayList<>();
        Map<String, String> previousIds = Map.of();
        renamesFollowed = 0;

        for (int releaseId : releaseIds) {
            Map<String, String> oldPathByNewPath = invertProduction(renamesInto.getOrDefault(releaseId, Map.of()));
            Map<String, String> currentIds = new HashMap<>();
            for (String path : filesByRelease.getOrDefault(releaseId, List.of())) {
                if (!ProductionFiles.isProduction(path)) {
                    continue;
                }
                String id = previousIds.get(path);
                if (id == null) {
                    String oldPath = oldPathByNewPath.get(path);
                    if (oldPath != null && previousIds.containsKey(oldPath)) {
                        id = previousIds.get(oldPath);
                        renamesFollowed++;
                    } else {
                        id = path;
                    }
                }
                currentIds.put(path, id);
                inventory.add(new ReleaseClass(releaseId, path, id));
            }
            previousIds = currentIds;
        }
        return inventory;
    }

    /** Quante rinomine sono state seguite nell'ultima costruzione (numero da riportare). */
    public int renamesFollowed() {
        return renamesFollowed;
    }

    /** Inverte le rinomine (nuovo → vecchio) tenendo solo quelle tra file di produzione. */
    private static Map<String, String> invertProduction(Map<String, String> oldToNew) {
        Map<String, String> newToOld = new HashMap<>();
        oldToNew.forEach((oldPath, newPath) -> {
            if (ProductionFiles.isProduction(oldPath) && ProductionFiles.isProduction(newPath)) {
                newToOld.put(newPath, oldPath);
            }
        });
        return newToOld;
    }
}