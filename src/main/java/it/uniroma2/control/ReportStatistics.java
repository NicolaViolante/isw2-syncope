package it.uniroma2.control;

import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.ReleaseSummary;
import it.uniroma2.entity.TicketVersions;

import java.util.ArrayList;
import java.util.List;

/** Fase 10: i numeri del dataset e di Proportion che servono al report. */
public final class ReportStatistics {

    private ReportStatistics() {
    }

    /** Per ogni release del dataset: classi, classi toccate nella release, righe buggy e somma di NSmells. */
    public static List<ReleaseSummary> byRelease(Dataset dataset, List<Release> datasetReleases) {
        int nr = dataset.featureNames().indexOf("NR");
        List<ReleaseSummary> summaries = new ArrayList<>();
        for (Release release : datasetReleases) {
            int classes = 0;
            int touched = 0;
            int buggy = 0;
            long smells = 0;
            for (DatasetRow r : dataset.rows()) {
                if (r.releaseId() != release.seqId()) {
                    continue;
                }
                classes++;
                if (!"0".equals(r.features().get(nr))) {
                    touched++;
                }
                if (r.buggy()) {
                    buggy++;
                }
                smells += r.nSmells();
            }
            summaries.add(new ReleaseSummary(release.seqId(), release.name(), release.date(), classes, touched,
                    buggy, smells));
        }
        return summaries;
    }

    /**
     * Ricalcola P dal file della Fase 8 (stessa formula di {@link TicketVersionResolver}): media di
     * (FV − IV)/(FV − OV) sui ticket con IV dalle AV ed FV ≠ OV. Serve da controllo incrociato.
     *
     * @return {P, numero di ticket usati}
     */
    public static double[] proportion(List<TicketVersions> versions) {
        double sum = 0;
        int count = 0;
        for (TicketVersions v : versions) {
            if (v.source() == TicketVersions.Source.AV && !v.fv().equals(v.ov())) {
                sum += (double) (v.fv() - v.iv()) / (v.fv() - v.ov());
                count++;
            }
        }
        return new double[] {count == 0 ? 0.0 : sum / count, count};
    }
}