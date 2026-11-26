package it.uniroma2.control;

import it.uniroma2.entity.Release;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Riconduce una Affected Version di Jira a una release della linea principale (Release ID), per discendenza:
 * <ul>
 *   <li>release tenuta: il suo Release ID;</li>
 *   <li>release di manutenzione scartata: l'ultima release tenuta della stessa linea (major.minor) uscita entro la
 *       sua data (es. 1.0.8 → 1.0.7), perché ne è il codice più qualche correzione;</li>
 *   <li>milestone: la release definitiva di cui è l'anteprima (es. 2.0.0-M1 → 2.0.0);</li>
 *   <li>versione mai rilasciata o sconosciuta: nessuna release (l'AV viene ignorata).</li>
 * </ul>
 */
public final class AffectedVersionMapper {

    private static final Pattern MILESTONE_SUFFIX = Pattern.compile("(?i)[-.]M\\d+.*$");

    private final Map<String, Release> byJiraId = new HashMap<>();
    private final List<Release> mainLine;

    /** @param allVersions tutte le versioni di Jira, con Release ID per le tenute e motivo per le scartate */
    public AffectedVersionMapper(List<Release> allVersions) {
        allVersions.forEach(r -> byJiraId.put(r.jiraId(), r));
        this.mainLine = allVersions.stream().filter(Release::isKept)
                .sorted(Comparator.comparingInt(Release::seqId)).toList();
    }

    public Optional<Integer> map(String jiraVersionId) {
        Release r = byJiraId.get(jiraVersionId);
        if (r == null || r.date() == null || r.major() < 0) {
            return Optional.empty();
        }
        if (r.isKept()) {
            return Optional.of(r.seqId());
        }
        if (MILESTONE_SUFFIX.matcher(r.name()).find()) {
            String finalName = MILESTONE_SUFFIX.matcher(r.name()).replaceAll("");
            return mainLine.stream().filter(k -> k.name().equals(finalName)).map(Release::seqId).findFirst();
        }
        // manutenzione: ultima release tenuta della stessa linea uscita entro la sua data
        return mainLine.stream()
                .filter(k -> k.major() == r.major() && k.minor() == r.minor() && !k.date().isAfter(r.date()))
                .map(Release::seqId).reduce((first, second) -> second);
    }
}