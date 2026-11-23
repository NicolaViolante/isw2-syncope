package it.uniroma2.jira;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Codice di esempio del prof (getReleaseInfo), adattato solo quanto basta per compilare
 * nel progetto: package, nome della classe, progetto SYNCOPE, API non deprecate.
 * La logica (release con data, ordinate per data, indice progressivo) è invariata:
 * i filtri della Fase 1 (rami di manutenzione, -M, stesso giorno) verranno aggiunti dopo.
 */
public class GetReleaseInfo {

    private static final Logger LOGGER = Logger.getLogger(GetReleaseInfo.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";

    private static final Map<LocalDateTime, String> releaseNames = new HashMap<>();
    private static final Map<LocalDateTime, String> releaseID = new HashMap<>();
    private static final List<LocalDateTime> releases = new ArrayList<>();

    private GetReleaseInfo() {
    }

    public static void main(String[] args) throws IOException, JSONException {
        // Riempie la lista con le date delle release e le ordina.
        // Ignora le release senza data.
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + PROJ_NAME;
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");
        for (int i = 0; i < versions.length(); i++) {
            JSONObject v = versions.getJSONObject(i);
            if (v.has("releaseDate")) {
                String name = v.has("name") ? v.get("name").toString() : "";
                String id = v.has("id") ? v.get("id").toString() : "";
                addRelease(v.get("releaseDate").toString(), name, id);
            }
        }
        // Ordina le release per data
        Collections.sort(releases);
        if (releases.size() < 6) {
            return;
        }

        String outname = PROJ_NAME + "VersionInfo.csv";
        try (FileWriter fileWriter = new FileWriter(outname, StandardCharsets.UTF_8)) {
            fileWriter.append("Index,Version ID,Version Name,Date\n");
            for (int i = 0; i < releases.size(); i++) {
                LocalDateTime date = releases.get(i);
                fileWriter.append(String.valueOf(i + 1)).append(",")
                        .append(releaseID.get(date)).append(",")
                        .append(releaseNames.get(date)).append(",")
                        .append(date.toString()).append("\n");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nella scrittura del CSV", e);
        }
    }

    private static void addRelease(String strDate, String name, String id) {
        LocalDateTime dateTime = LocalDate.parse(strDate).atStartOfDay();
        if (!releases.contains(dateTime)) {
            releases.add(dateTime);
        }
        releaseNames.put(dateTime, name);
        releaseID.put(dateTime, id);
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = URI.create(url).toURL().openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return new JSONObject(readAll(rd));
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
}
