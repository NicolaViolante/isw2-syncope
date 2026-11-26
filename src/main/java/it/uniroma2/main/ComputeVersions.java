package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.control.TicketVersionResolver;
import it.uniroma2.entity.TicketVersions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fase 8 – OV, FV, IV e Proportion.
 *
 * <p>Per ogni ticket bug della Fase 2 calcola Opening, Fix e Injected Version sulla linea principale della Fase 1;
 * la IV viene dalla prima Affected Version coerente oppure da Proportion Total.
 *
 * <p>Input in data/: SYNCOPE_releases_all.csv, SYNCOPE_tickets.csv. Output: output/SYNCOPE_versions.csv.
 */
public final class ComputeVersions {

    private static final Logger LOGGER = Logger.getLogger(ComputeVersions.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_versions.csv");

    private ComputeVersions() {
    }

    public static void main(String[] args) throws IOException {
        TicketVersionResolver resolver = new TicketVersionResolver(
                CsvReader.readAllReleases(DATA_DIR.resolve(PROJ_NAME + "_releases_all.csv")));
        List<TicketVersions> versions =
                resolver.resolve(CsvReader.readTickets(DATA_DIR.resolve(PROJ_NAME + "_tickets.csv")));
        CsvWriter.writeVersions(versions, OUTPUT_FILE);

        Map<TicketVersions.Source, Integer> bySource = new EnumMap<>(TicketVersions.Source.class);
        versions.forEach(v -> bySource.merge(v.source(), 1, Integer::sum));
        long used = versions.stream().filter(v -> !v.isExcluded()).count();
        long estimated = used - bySource.getOrDefault(TicketVersions.Source.AV, 0);
        LOGGER.info(() -> String.format(Locale.ROOT, "Ticket: %d | per origine della IV: %s", versions.size(), bySource));
        LOGGER.info(() -> String.format(Locale.ROOT, "P = %.4f (su %d ticket con AV coerenti) | IV da Proportion: %d su %d "
                        + "ticket usati (%.1f%%)", resolver.proportion(), resolver.proportionTickets(), estimated, used,
                100.0 * estimated / used));
    }
}