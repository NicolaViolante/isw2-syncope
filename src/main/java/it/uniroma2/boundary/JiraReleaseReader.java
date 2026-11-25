package it.uniroma2.boundary;

import it.uniroma2.entity.Release;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Boundary verso Jira: legge le versioni di un progetto e le trasforma in entità {@link Release}. */
public final class JiraReleaseReader {

    private JiraReleaseReader() {
    }

    /** Scarica tutte le versioni del progetto da Jira (anche quelle non rilasciate). */
    public static List<Release> fetchVersions(String project) throws IOException {
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
            versions.add(Release.fromJira(v.optString("id", ""), v.optString("name", ""), date));
        }
        return versions;
    }
}
