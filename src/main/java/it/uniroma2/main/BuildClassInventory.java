package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.GitRepository;
import it.uniroma2.control.ClassInventoryBuilder;
import it.uniroma2.control.ProductionFiles;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.ReleaseClass;
import it.uniroma2.entity.Snapshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Fase 3 – Snapshot e classi.
 *
 * <p>Per ognuna delle release del dataset (lette da data/SYNCOPE_releases.csv, congelato nella Fase 1):
 * trova lo snapshot (tag "syncope-NOME", o in mancanza l'ultimo commit di master), elenca le classi
 * Java di produzione e assegna a ogni classe un'identità stabile che segue le rinomine.
 * Il repository di Syncope viene solo letto, senza checkout.
 *
 * <p>Argomento opzionale: percorso della copia locale di Syncope (default ~/IdeaProjects/syncope).
 *
 * <p>Output in output/: SYNCOPE_snapshots.csv, SYNCOPE_classes.csv, SYNCOPE_modules.csv.
 */
public final class BuildClassInventory {

    private static final Logger LOGGER = Logger.getLogger(BuildClassInventory.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path RELEASES_FILE = Path.of("data", PROJ_NAME + "_releases.csv");
    private static final Path OUTPUT_DIR = Path.of("output");

    private BuildClassInventory() {
    }

    public static void main(String[] args) throws IOException {
        File syncope = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "IdeaProjects/syncope");
        List<Release> releases = CsvReader.readMainLine(RELEASES_FILE, true);

        List<Snapshot> snapshots = new ArrayList<>();
        Map<Integer, List<String>> filesByRelease = new HashMap<>();
        Map<Integer, Map<String, String>> renamesInto = new HashMap<>();
        try (GitRepository git = new GitRepository(syncope)) {
            Snapshot previous = null;
            for (Release release : releases) {
                Snapshot snapshot = git.snapshotOf(release);
                snapshots.add(snapshot);
                filesByRelease.put(release.seqId(), git.javaFiles(snapshot));
                if (previous != null) {
                    renamesInto.put(release.seqId(), git.renamesBetween(previous, snapshot));
                }
                previous = snapshot;
            }
        }

        List<Integer> releaseIds = releases.stream().map(Release::seqId).toList();
        ClassInventoryBuilder builder = new ClassInventoryBuilder();
        List<ReleaseClass> inventory = builder.build(releaseIds, filesByRelease, renamesInto);

        Map<Integer, Integer> classesByRelease = new HashMap<>();
        Map<String, Map<Integer, Integer>> countsByModule = new TreeMap<>();
        for (ReleaseClass c : inventory) {
            classesByRelease.merge(c.releaseId(), 1, Integer::sum);
            countsByModule.computeIfAbsent(ProductionFiles.module(c.path()), m -> new HashMap<>())
                    .merge(c.releaseId(), 1, Integer::sum);
        }

        CsvWriter.writeSnapshots(snapshots, classesByRelease, OUTPUT_DIR.resolve(PROJ_NAME + "_snapshots.csv"));
        CsvWriter.writeClasses(inventory, OUTPUT_DIR.resolve(PROJ_NAME + "_classes.csv"));
        CsvWriter.writeModuleCounts(countsByModule, releaseIds, OUTPUT_DIR.resolve(PROJ_NAME + "_modules.csv"));

        long fromTag = snapshots.stream().filter(Snapshot::fromTag).count();
        long distinctClasses = inventory.stream().map(ReleaseClass::classId).distinct().count();
        LOGGER.info(() -> String.format(
                "Release: %d (snapshot da tag: %d) | righe classe-release: %d | classi distinte: %d | "
                        + "rinomine seguite: %d | classi nella prima release: %d, nell'ultima: %d",
                releases.size(), fromTag, inventory.size(), distinctClasses, builder.renamesFollowed(),
                classesByRelease.getOrDefault(releaseIds.get(0), 0),
                classesByRelease.getOrDefault(releaseIds.get(releaseIds.size() - 1), 0)));
    }
}