package it.uniroma2.main;

import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.JiraTicketReader;
import it.uniroma2.entity.Ticket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fase 2 – Ticket. Punto di avvio, evoluzione dell'esempio del prof (RetrieveTicketsID).
 *
 * <p>Legge da Jira i ticket di bug della query del prof (boundary) e li scrive in
 * output/SYNCOPE_tickets.csv (boundary), con data di creazione, data di risoluzione,
 * Affected Version e Fix Version.
 */
public final class RetrieveTicketsID {

    private static final Logger LOGGER = Logger.getLogger(RetrieveTicketsID.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_tickets.csv");

    private RetrieveTicketsID() {
    }

    public static void main(String[] args) throws IOException {
        List<Ticket> tickets = JiraTicketReader.fetchTickets(PROJ_NAME);
        CsvWriter.writeTickets(tickets, OUTPUT_FILE);

        long withAv = tickets.stream().filter(Ticket::hasAffectedVersions).count();
        long withoutResolution = tickets.stream().filter(t -> t.resolved() == null).count();
        LOGGER.info(() -> String.format(
                "Ticket: %d | con AV: %d (%.1f%%) | senza data di risoluzione: %d",
                tickets.size(), withAv, 100.0 * withAv / Math.max(1, tickets.size()), withoutResolution));
    }
}