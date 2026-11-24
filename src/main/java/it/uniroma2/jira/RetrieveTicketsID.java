package it.uniroma2.jira;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Codice di esempio del prof (RetrieveTicketsID), adattato solo quanto basta per compilare
 * nel progetto: package, progetto SYNCOPE, paginazione corretta (maxResults=1000 invece di
 * maxResults=j). La lettura del JSON ora usa {link JiraClient}, condiviso con GetReleaseInfo.
 * Per ora stampa solo le chiavi dei ticket: verrà esteso nella Fase 2.
 */
public final class RetrieveTicketsID {

    private static final Logger LOGGER = Logger.getLogger(RetrieveTicketsID.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final int PAGE_SIZE = 1000;

    private RetrieveTicketsID() {
    }

    public static void main(String[] args) throws IOException {
        int i = 0;
        int total;
        // Jira restituisce al massimo 1000 ticket per richiesta: si cicla a pagine
        do {
            int j = i + PAGE_SIZE;
            String url = JiraClient.BASE_URL + "search?jql=project=%22"
                    + PROJ_NAME + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22"
                    + "&fields=key,resolutiondate,versions,created"
                    + "&startAt=" + i + "&maxResults=" + PAGE_SIZE;
            JSONObject json = JiraClient.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                String key = issues.getJSONObject(i % PAGE_SIZE).get("key").toString();
                LOGGER.info(key);
            }
        } while (i < total);
    }
}