package it.uniroma2.boundary;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Accesso alle API REST di Jira di Apache. Contiene il codice di lettura del JSON degli esempi
 * del prof (readJsonFromUrl, readAll), messo in un'unica classe per non duplicarlo
 * tra GetReleaseInfo e RetrieveTicketsID.
 */
public final class JiraClient {

    public static final String BASE_URL = "https://issues.apache.org/jira/rest/api/2/";

    private JiraClient() {
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException {
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