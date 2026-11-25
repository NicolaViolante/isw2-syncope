package it.uniroma2.control;

import it.uniroma2.entity.ProcessMetrics;
import it.uniroma2.entity.Touch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calcola le metriche di processo di una classe su un insieme di commit.
 *
 * <ul>
 *   <li>NR: commit che toccano la classe; NFix: quelli che citano un ticket bug;</li>
 *   <li>NAuth: email distinte degli autori (due email diverse sono due autori);</li>
 *   <li>LOCAdded, Churn (aggiunte − cancellate): somme sui commit; MaxLOCAdded, MaxChurn: massimi;</li>
 *   <li>ChangeSetSize: somma dei file modificati nei commit (tutti i tipi di file); MaxChangeSet: massimo.</li>
 * </ul>
 */
public final class ProcessMetricsCalculator {

    private final FixCommitFinder fixes;

    public ProcessMetricsCalculator(FixCommitFinder fixes) {
        this.fixes = fixes;
    }

    public ProcessMetrics compute(List<Touch> touches) {
        if (touches.isEmpty()) {
            return ProcessMetrics.EMPTY;
        }
        Set<String> authors = new HashSet<>();
        int nFix = 0;
        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int maxChurn = Integer.MIN_VALUE;
        int changeSet = 0;
        int maxChangeSet = 0;
        for (Touch t : touches) {
            if (fixes.isFix(t.commit().message())) {
                nFix++;
            }
            authors.add(t.commit().authorEmail());
            locAdded += t.change().added();
            maxLocAdded = Math.max(maxLocAdded, t.change().added());
            churn += t.change().churn();
            maxChurn = Math.max(maxChurn, t.change().churn());
            changeSet += t.commit().filesChanged();
            maxChangeSet = Math.max(maxChangeSet, t.commit().filesChanged());
        }
        return new ProcessMetrics(touches.size(), nFix, authors.size(), locAdded, maxLocAdded,
                churn, maxChurn, changeSet, maxChangeSet);
    }
}