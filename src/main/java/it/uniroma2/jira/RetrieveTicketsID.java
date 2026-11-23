package it.uniroma2.jira;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Codice di esempio del prof (RetrieveTicketsID), adattato solo quanto basta per compilare
 * nel progetto: package, progetto SYNCOPE, API non deprecate, paginazione corretta
 * (maxResults=1000 invece di maxResults=j). Per ora stampa solo le chiavi dei ticket:
 * i campi fixVersions e il salvataggio su CSV verranno aggiunti nella Fase 2.
 */
public class RetrieveTicketsID {

    private static final Logger LOGGER = Logger.getLogger(RetrieveTicketsID.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final int PAGE_SIZE = 1000;

    private RetrieveTicketsID() {
    }

    public static void main(String[] args) throws IOException, JSONException {
        int i = 0;
        int total;
        // Jira restituisce al massimo 1000 ticket per richiesta: si cicla a pagine
        do {
            int j = i + PAGE_SIZE;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + PROJ_NAME + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22"
                    + "&fields=key,resolutiondate,versions,created"
                    + "&startAt=" + i + "&maxResults=" + PAGE_SIZE;
            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                String key = issues.getJSONObject(i % PAGE_SIZE).get("key").toString();
                LOGGER.info(key);
            }
        } while (i < total);
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
