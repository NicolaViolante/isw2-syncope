package it.uniroma2.control;

import it.uniroma2.entity.ClassSmells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Associa a ogni classe dell'inventario il numero di smell letto da SonarCloud.
 *
 * <p>Un file restituito da SonarCloud senza la misura ha 0 smell; un file che SonarCloud non ha restituito
 * resta senza valore, così il problema emerge nel log invece di diventare uno 0 silenzioso.
 */
public final class SmellAssigner {

    private SmellAssigner() {
    }

    /**
     * @param releaseId    la release
     * @param classesAtTag percorso → Class ID delle classi della release (inventario della Fase 3)
     * @param measured     percorso → numero di smell, per i file restituiti da SonarCloud
     */
    public static List<ClassSmells> assign(int releaseId, Map<String, String> classesAtTag,
                                           Map<String, Integer> measured) {
        List<ClassSmells> rows = new ArrayList<>();
        for (Map.Entry<String, String> c : classesAtTag.entrySet()) {
            rows.add(new ClassSmells(releaseId, c.getKey(), c.getValue(), measured.get(c.getKey())));
        }
        return rows;
    }
}