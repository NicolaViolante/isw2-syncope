package it.uniroma2.jira;

import it.uniroma2.model.Release;
import it.uniroma2.release.ReleaseSelector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fase 1 – Release. Evoluzione dell'esempio del prof (getReleaseInfo).
 *
 * <p>Come nell'esempio: scarica da Jira le versioni del progetto, le ordina per data e assegna
 * un indice progressivo. In più applica le decisioni della Fase 1 (scarto di milestone e
 * release di manutenzione, ID dopo i filtri, taglio al 33%) tramite {@link ReleaseSelector}.
 * Rispetto all'esempio corregge anche un difetto: le release erano indicizzate per data in una
 * HashMap, quindi due release uscite lo stesso giorno si sovrascrivevano a vicenda.
 *
 * <p>Produce due file nella cartella output/:
 * <ul>
 *   <li>SYNCOPE_releases_all.csv: tutte le versioni di Jira, con il motivo di ogni scarto;</li>
 *   <li>SYNCOPE_releases.csv: la linea principale con gli ID 1, 2, 3… e la colonna InDataset.</li>
 * </ul>
 */
public final class GetReleaseInfo {

    private static final Logger LOGGER = Logger.getLogger(GetReleaseInfo.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path OUTPUT_DIR = Path.of("output");

    private GetReleaseInfo() {
    }

    public static void main(String[] args) throws IOException {
        List<Release> versions = fetchVersions(PROJ_NAME);
        List<Release> selected = ReleaseSelector.select(versions);
        List<Release> mainLine = ReleaseSelector.mainLine(selected);
        int datasetSize = ReleaseSelector.datasetSize(mainLine.size());

        Files.createDirectories(OUTPUT_DIR);
        writeAll(selected, OUTPUT_DIR.resolve(PROJ_NAME + "_releases_all.csv"));
        writeMainLine(mainLine, datasetSize, OUTPUT_DIR.resolve(PROJ_NAME + "_releases.csv"));

        LOGGER.info(() -> String.format(
                "Versioni su Jira: %d | linea principale: %d | release nel dataset: %d (%s → %s)",
                versions.size(), mainLine.size(), datasetSize,
                mainLine.get(0).name(), mainLine.get(datasetSize - 1).name()));
    }

    /** Scarica tutte le versioni del progetto da Jira (anche quelle non rilasciate). */
    static List<Release> fetchVersions(String project) throws IOException {
        JSONObject json = JiraClient.readJsonFromUrl(JiraClient.BASE_URL + "project/" + project);
        return parseVersions(json.getJSONArray("versions"));
    }

    static List<Release> parseVersions(JSONArray array) {
        List<Release> versions = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject v = array.getJSONObject(i);
            boolean released = v.optBoolean("released", false);
            LocalDate date = released && v.has("releaseDate")
                    ? LocalDate.parse(v.getString("releaseDate"))
                    : null;
            versions.add(ReleaseSelector.fromJira(v.optString("id", ""), v.optString("name", ""), date));
        }
        return versions;
    }

    private static void writeAll(List<Release> selected, Path file) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("Version ID,Version Name,Date,Line,Kept,Release ID,Discard Reason");
            for (Release r : selected) {
                out.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        r.jiraId(), r.name(),
                        r.date() == null ? "" : r.date(),
                        r.major() < 0 ? "" : r.line(),
                        r.isKept() ? "yes" : "no",
                        r.isKept() ? String.valueOf(r.seqId()) : "",
                        r.isKept() ? "" : r.discardReason());
            }
        }
    }

    private static void writeMainLine(List<Release> mainLine, int datasetSize, Path file) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("Release ID,Version ID,Version Name,Date,InDataset");
            for (Release r : mainLine) {
                out.printf("%d,%s,%s,%s,%s%n", r.seqId(), r.jiraId(), r.name(), r.date(),
                        r.seqId() <= datasetSize ? "yes" : "no");
            }
        }
    }
}