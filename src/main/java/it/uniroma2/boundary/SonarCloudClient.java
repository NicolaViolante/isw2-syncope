package it.uniroma2.boundary;

import it.uniroma2.entity.SmellIssue;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legge da SonarCloud lo stato delle analisi e i code smell, con le API web.
 *
 * <p>Il token viene passato dal chiamante (letto dalla variabile d'ambiente SONAR_TOKEN) e usato solo
 * nell'intestazione delle richieste.
 */
public final class SonarCloudClient {

    public static final String SONARCLOUD = "https://sonarcloud.io";
    private static final int PAGE_SIZE = 500;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MAX_WAIT = Duration.ofMinutes(30);
    /** Tentativi per richiesta, contro gli errori di rete momentanei. */
    private static final int ATTEMPTS = 3;
    private static final Duration WAIT_BETWEEN_ATTEMPTS = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String token;
    /** Senza limiti di tempo una connessione bloccata fermerebbe il programma per sempre. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public SonarCloudClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    /** Attende che SonarCloud finisca di elaborare l'analisi; errore se fallisce o supera 30 minuti. */
    public void waitForTask(String taskId) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            String status = get("/api/ce/task?id=" + encode(taskId)).getJSONObject("task").getString("status");
            switch (status) {
                case "SUCCESS":
                    return;
                case "FAILED", "CANCELED":
                    throw new IOException("Elaborazione su SonarCloud " + status + " (task " + taskId + ")");
                default:
                    Thread.sleep(POLL_INTERVAL.toMillis()); // PENDING o IN_PROGRESS
            }
        }
        throw new IOException("Elaborazione su SonarCloud non terminata in " + MAX_WAIT.toMinutes() + " minuti");
    }

    /** Numero di code smell per file dell'ultima analisi del progetto: percorso → smell. */
    public Map<String, Integer> fileSmells(String projectKey) throws IOException, InterruptedException {
        Map<String, Integer> smells = new HashMap<>();
        int page = 1;
        int total;
        do {
            JSONObject json = get("/api/measures/component_tree?component=" + encode(projectKey)
                    + "&metricKeys=code_smells&qualifiers=FIL&ps=" + PAGE_SIZE + "&p=" + page);
            JSONArray components = json.getJSONArray("components");
            for (int i = 0; i < components.length(); i++) {
                JSONObject c = components.getJSONObject(i);
                int value = 0;
                JSONArray measures = c.optJSONArray("measures");
                if (measures != null && !measures.isEmpty()) {
                    value = Integer.parseInt(measures.getJSONObject(0).getString("value"));
                }
                smells.put(c.getString("path"), value);
            }
            total = json.getJSONObject("paging").getInt("total");
            page++;
        } while ((page - 1) * PAGE_SIZE < total);
        return smells;
    }

    /**
     * Elenco degli smell aperti di un file (regola, severità, riga, messaggio, effort), dall'ultima analisi.
     * Non serve per NSmells: è la diagnostica da dare all'LLM nella Milestone 4.
     */
    public List<SmellIssue> fileIssues(String projectKey, String path) throws IOException, InterruptedException {
        List<SmellIssue> issues = new ArrayList<>();
        int page = 1;
        int total;
        do {
            JSONObject json = get("/api/issues/search?componentKeys=" + encode(projectKey + ":" + path)
                    + "&types=CODE_SMELL&resolved=false&ps=" + PAGE_SIZE + "&p=" + page);
            JSONArray list = json.getJSONArray("issues");
            for (int i = 0; i < list.length(); i++) {
                JSONObject is = list.getJSONObject(i);
                issues.add(new SmellIssue(is.getString("rule"), is.optString("severity"), is.optInt("line", 0),
                        is.optString("message"), is.optString("effort")));
            }
            total = json.optInt("total", json.has("paging") ? json.getJSONObject("paging").getInt("total") : 0);
            page++;
        } while ((page - 1) * PAGE_SIZE < total);
        return issues;
    }

    private JSONObject get(String pathAndQuery) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return getOnce(pathAndQuery);
            } catch (IOException e) {
                last = e;
                if (attempt < ATTEMPTS) {
                    Thread.sleep(WAIT_BETWEEN_ATTEMPTS.toMillis());
                }
            }
        }
        throw last;
    }

    private JSONObject getOnce(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .header("Authorization", "Bearer " + token)
                .timeout(REQUEST_TIMEOUT)
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("SonarCloud ha risposto " + response.statusCode() + " a " + pathAndQuery);
        }
        return new JSONObject(response.body());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}