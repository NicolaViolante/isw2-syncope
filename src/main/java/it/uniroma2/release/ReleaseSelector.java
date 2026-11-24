package it.uniroma2.release;

import it.uniroma2.model.Release;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Costruisce la sequenza di release della linea principale (Fase 1).
 *
 * <p>Regole, nell'ordine:
 * <ol>
 *   <li>si scartano le versioni non rilasciate o senza data;</li>
 *   <li>si scartano le release "milestone" (-M1, .M5…);</li>
 *   <li>si ordinano per data (a parità di data, prima la linea più recente);</li>
 *   <li>si scarta una release se prima di lei (o nello stesso giorno) è già uscita una release
 *       di una linea più recente: è una release di manutenzione;</li>
 *   <li>si scarta una release uscita lo stesso giorno di una release già tenuta della stessa linea;</li>
 *   <li>le release rimaste ricevono un ID progressivo 1, 2, 3…</li>
 * </ol>
 */
public final class ReleaseSelector {

    public static final String REASON_NOT_RELEASED = "non rilasciata o senza data";
    public static final String REASON_MILESTONE = "release milestone (-M)";
    public static final String REASON_MAINTENANCE = "manutenzione: già uscita la linea ";
    public static final String REASON_SAME_DAY = "stesso giorno di ";

    private static final Pattern MILESTONE = Pattern.compile("(?i)[-.]M\\d+");
    private static final Pattern LINE = Pattern.compile("^(\\d+)\\.(\\d+)");

    private ReleaseSelector() {
    }

    /** Costruisce una Release dai dati grezzi di Jira; major/minor = -1 se il nome non è numerico. */
    public static Release fromJira(String jiraId, String name, LocalDate date) {
        var m = LINE.matcher(name);
        int major = -1;
        int minor = -1;
        if (m.find()) {
            major = Integer.parseInt(m.group(1));
            minor = Integer.parseInt(m.group(2));
        }
        return new Release(jiraId, name, date, major, minor, 0, null);
    }

    /**
     * Applica tutte le regole e restituisce tutte le versioni, ordinate per data:
     * quelle tenute hanno seqId &gt; 0, quelle scartate hanno il motivo dello scarto.
     */
    public static List<Release> select(List<Release> versions) {
        List<Release> result = new ArrayList<>();
        List<Release> candidates = new ArrayList<>();

        for (Release r : versions) {
            if (r.date() == null || r.major() < 0) {
                result.add(r.discarded(REASON_NOT_RELEASED));
            } else if (MILESTONE.matcher(r.name()).find()) {
                result.add(r.discarded(REASON_MILESTONE));
            } else {
                candidates.add(r);
            }
        }

        candidates.sort(Comparator.comparing(Release::date)
                .thenComparing(Release::major, Comparator.reverseOrder())
                .thenComparing(Release::minor, Comparator.reverseOrder()));

        Release newestLine = null;
        Map<LocalDate, Release> keptByDate = new HashMap<>();
        int seq = 0;
        for (Release r : candidates) {
            if (newestLine != null && r.compareLineTo(newestLine) < 0) {
                result.add(r.discarded(REASON_MAINTENANCE + newestLine.line()));
            } else if (keptByDate.containsKey(r.date())) {
                result.add(r.discarded(REASON_SAME_DAY + keptByDate.get(r.date()).name()));
            } else {
                seq++;
                Release kept = r.withSeqId(seq);
                result.add(kept);
                keptByDate.put(r.date(), kept);
                newestLine = kept;
            }
        }

        result.sort(Comparator.comparing(Release::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Release::name));
        return result;
    }

    /** Le sole release tenute, in ordine di ID. */
    public static List<Release> mainLine(List<Release> selected) {
        return selected.stream()
                .filter(Release::isKept)
                .sorted(Comparator.comparingInt(Release::seqId))
                .toList();
    }

    /**
     * Numero di release che diventano righe del dataset: il primo terzo della linea principale
     * (il prof indica di ignorare l'ultimo 66%), arrotondato all'intero più vicino.
     */
    public static int datasetSize(int mainLineSize) {
        return (int) Math.round(mainLineSize / 3.0);
    }
}
