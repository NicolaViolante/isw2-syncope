package it.uniroma2.main;

import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.JiraReleaseReader;
import it.uniroma2.control.ReleaseSelector;
import it.uniroma2.entity.Release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fase 1 – Release. Punto di avvio, evoluzione dell'esempio del prof (getReleaseInfo).
 *
 * <p>Coordina le tre parti del pattern BCE: legge le versioni da Jira (boundary), applica le regole
 * della Fase 1 (control: scarto di milestone e release di manutenzione, ID dopo i filtri, taglio a
 * un terzo) e scrive i CSV (boundary).
 *
 * <p>Rispetto all'esempio del prof corregge anche un difetto: le release erano indicizzate per data
 * in una HashMap, quindi due release uscite lo stesso giorno si sovrascrivevano a vicenda.
 *
 * <p>Output in output/: SYNCOPE_releases_all.csv (tutte le versioni con il motivo di ogni scarto)
 * e SYNCOPE_releases.csv (linea principale con gli ID e la colonna InDataset).
 */
public final class GetReleaseInfo {

    private static final Logger LOGGER = Logger.getLogger(GetReleaseInfo.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path OUTPUT_DIR = Path.of("output");

    private GetReleaseInfo() {
    }

    public static void main(String[] args) throws IOException {
        List<Release> versions = JiraReleaseReader.fetchVersions(PROJ_NAME);
        List<Release> selected = ReleaseSelector.select(versions);
        List<Release> mainLine = ReleaseSelector.mainLine(selected);
        int datasetSize = ReleaseSelector.datasetSize(mainLine.size());

        CsvWriter.writeAllReleases(selected, OUTPUT_DIR.resolve(PROJ_NAME + "_releases_all.csv"));
        CsvWriter.writeMainLine(mainLine, datasetSize, OUTPUT_DIR.resolve(PROJ_NAME + "_releases.csv"));

        LOGGER.info(() -> String.format(
                "Versioni su Jira: %d | linea principale: %d | release nel dataset: %d (%s → %s)",
                versions.size(), mainLine.size(), datasetSize,
                mainLine.get(0).name(), mainLine.get(datasetSize - 1).name()));
    }
}
