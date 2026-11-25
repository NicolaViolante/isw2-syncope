package it.uniroma2.boundary;

import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.Ticket.JiraVersion;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Boundary verso Jira: legge i ticket di bug della query del workflow del prof
 * (Bug, Closed o Resolved, Fixed) e li trasforma in entità {@link Ticket}.
 *
 * <p>La paginazione avanza del numero di ticket effettivamente restituiti da Jira (non di un 1000
 * fisso), così nessun ticket va perso se Jira ne restituisce meno per pagina.
 * I ticket sono ordinati per data di creazione, per avere pagine stabili.
 */
public final class JiraTicketReader {

    private static final int PAGE_SIZE = 1000;
    private static final String FIELDS = "key,created,resolutiondate,versions,fixVersions";

    private JiraTicketReader() {
    }

    static String jql(String project) {
        return "project = " + project + " AND issuetype = Bug AND status in (Resolved, Closed)"
                + " AND resolution = Fixed ORDER BY created ASC";
    }

    /** Scarica tutti i ticket della query, pagina per pagina. */
    public static List<Ticket> fetchTickets(String project) throws IOException {
        String query = URLEncoder.encode(jql(project), StandardCharsets.UTF_8);
        List<Ticket> tickets = new ArrayList<>();
        int total;
        do {
            String url = JiraClient.BASE_URL + "search?jql=" + query
                    + "&fields=" + FIELDS
                    + "&startAt=" + tickets.size() + "&maxResults=" + PAGE_SIZE;
            JSONObject json = JiraClient.readJsonFromUrl(url);
            total = json.getInt("total");
            JSONArray issues = json.getJSONArray("issues");
            if (issues.length() == 0) {
                break; // protezione: Jira non restituisce più ticket
            }
            for (int i = 0; i < issues.length(); i++) {
                tickets.add(parseIssue(issues.getJSONObject(i)));
            }
        } while (tickets.size() < total);
        return tickets;
    }

    static Ticket parseIssue(JSONObject issue) {
        JSONObject fields = issue.getJSONObject("fields");
        return new Ticket(
                issue.getString("key"),
                parseDay(fields.optString("created", "")),
                parseDay(fields.optString("resolutiondate", "")),
                parseVersions(fields.optJSONArray("versions")),
                parseVersions(fields.optJSONArray("fixVersions")));
    }

    /** Da "2013-05-10T12:34:56.000+0000" tiene solo il giorno: anche le release hanno solo la data. */
    static LocalDate parseDay(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) {
            return null;
        }
        return LocalDate.parse(timestamp.substring(0, 10));
    }

    static List<JiraVersion> parseVersions(JSONArray array) {
        List<JiraVersion> versions = new ArrayList<>();
        if (array == null) {
            return versions;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject v = array.getJSONObject(i);
            versions.add(new JiraVersion(v.optString("id", ""), v.optString("name", "")));
        }
        return versions;
    }
}