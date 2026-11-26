package it.uniroma2.control;

import it.uniroma2.entity.Release;
import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.TicketVersions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Fase 8: OV, FV e IV di ogni ticket sulla linea principale, con Proportion Total.
 *
 * <ul>
 *   <li>OV: ultima release con data uguale o precedente all'apertura del ticket;</li>
 *   <li>FV: prima release uscita dopo il giorno di risoluzione;</li>
 *   <li>IV: prima AV ricondotta alla linea principale ({@link AffectedVersionMapper}) se è ≤ OV; altrimenti, e per
 *       i ticket senza AV, IV = FV − P·(FV − OV), arrotondata e limitata a [1, OV];</li>
 *   <li>P: media di (FV − IV)/(FV − OV) sui ticket con AV coerenti (di qualunque categoria), esclusi quelli con
 *       FV = OV;</li>
 *   <li>ticket senza OV (aperti prima della prima release) o senza FV (risolti dopo l'ultima) sono esclusi.</li>
 * </ul>
 */
public final class TicketVersionResolver {

    private final List<Release> mainLine;
    private final AffectedVersionMapper mapper;
    private double proportion;
    private int proportionTickets;

    public TicketVersionResolver(List<Release> allVersions) {
        this.mainLine = allVersions.stream().filter(Release::isKept)
                .sorted(Comparator.comparingInt(Release::seqId)).toList();
        this.mapper = new AffectedVersionMapper(allVersions);
    }

    public List<TicketVersions> resolve(List<Ticket> tickets) {
        List<TicketVersions> partial = new ArrayList<>();
        double sum = 0;
        int count = 0;
        for (Ticket t : tickets) {
            Integer ov = openingVersion(t.created());
            Integer fv = fixVersion(t.resolved());
            if (ov == null) {
                partial.add(new TicketVersions(t.key(), null, fv, null, TicketVersions.Source.EXCLUDED_NO_OV));
                continue;
            }
            if (fv == null) {
                partial.add(new TicketVersions(t.key(), ov, null, null, TicketVersions.Source.EXCLUDED_NO_FV));
                continue;
            }
            Optional<Integer> firstAv = t.affectedVersions().stream()
                    .map(v -> mapper.map(v.id())).flatMap(Optional::stream).min(Integer::compare);
            if (firstAv.isEmpty()) {
                partial.add(new TicketVersions(t.key(), ov, fv, null, TicketVersions.Source.PROPORTION_NO_AV));
            } else if (firstAv.get() > ov) {
                partial.add(new TicketVersions(t.key(), ov, fv, null, TicketVersions.Source.PROPORTION_INCOHERENT_AV));
            } else {
                int iv = firstAv.get();
                partial.add(new TicketVersions(t.key(), ov, fv, iv, TicketVersions.Source.AV));
                if (!fv.equals(ov)) {
                    sum += (double) (fv - iv) / (fv - ov);
                    count++;
                }
            }
        }
        if (count == 0) {
            throw new IllegalStateException("Nessun ticket con AV coerenti: impossibile calcolare P");
        }
        proportion = sum / count;
        proportionTickets = count;

        List<TicketVersions> result = new ArrayList<>();
        for (TicketVersions v : partial) {
            boolean estimate = v.source() == TicketVersions.Source.PROPORTION_NO_AV
                    || v.source() == TicketVersions.Source.PROPORTION_INCOHERENT_AV;
            result.add(estimate ? new TicketVersions(v.key(), v.ov(), v.fv(), estimateIv(v.ov(), v.fv()), v.source()) : v);
        }
        return result;
    }

    private int estimateIv(int ov, int fv) {
        int estimated = (int) Math.round(fv - (fv - ov) * proportion);
        return Math.max(1, Math.min(estimated, ov));
    }

    private Integer openingVersion(LocalDate created) {
        Integer ov = null;
        for (Release r : mainLine) {
            if (!r.date().isAfter(created)) {
                ov = r.seqId();
            }
        }
        return ov;
    }

    private Integer fixVersion(LocalDate resolved) {
        for (Release r : mainLine) {
            if (r.date().isAfter(resolved)) {
                return r.seqId();
            }
        }
        return null;
    }

    /** P calcolato dall'ultima chiamata a resolve. */
    public double proportion() {
        return proportion;
    }

    /** Numero di ticket usati per calcolare P. */
    public int proportionTickets() {
        return proportionTickets;
    }
}