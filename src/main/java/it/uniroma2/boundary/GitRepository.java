package it.uniroma2.boundary;

import it.uniroma2.entity.Release;
import it.uniroma2.entity.Snapshot;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boundary verso il repository Git di Syncope (copia locale della fork), tramite JGit.
 *
 * <p>Il repository viene solo letto: gli snapshot si leggono direttamente dagli oggetti di Git,
 * senza fare checkout, quindi la copia di lavoro non viene mai modificata.
 */
public final class GitRepository implements AutoCloseable {

    private static final String TAG_PREFIX = "refs/tags/syncope-";
    private static final String MASTER = "refs/heads/master";
    private static final String JAVA = ".java";
    /** Limite alto per il rilevamento delle rinomine: con il default di JGit (400) i diff grandi lo salterebbero. */
    private static final int RENAME_LIMIT = 20_000;

    private final Repository repository;

    public GitRepository(File workTree) throws IOException {
        this.repository = new FileRepositoryBuilder()
                .setGitDir(new File(workTree, ".git"))
                .readEnvironment()
                .build();
    }

    /**
     * Lo snapshot di una release: il commit del tag "syncope-NOME"; se il tag manca, l'ultimo commit
     * di master fino al giorno del rilascio compreso.
     */
    public Snapshot snapshotOf(Release release) throws IOException {
        Ref tag = repository.exactRef(TAG_PREFIX + release.name());
        try (RevWalk walk = new RevWalk(repository)) {
            if (tag != null) {
                RevCommit commit = walk.parseCommit(tag.getObjectId()); // risolve anche i tag annotati
                return new Snapshot(release, tag.getName().substring("refs/tags/".length()),
                        commit.getName(), dayOf(commit), true);
            }
            RevCommit commit = lastCommitOnMasterUntil(walk, release.date());
            return new Snapshot(release, "master", commit.getName(), dayOf(commit), false);
        }
    }

    private RevCommit lastCommitOnMasterUntil(RevWalk walk, LocalDate day) throws IOException {
        Ref master = repository.exactRef(MASTER);
        walk.markStart(walk.parseCommit(master.getObjectId()));
        walk.sort(RevSort.COMMIT_TIME_DESC);
        for (RevCommit commit : walk) {
            if (!dayOf(commit).isAfter(day)) {
                return commit;
            }
        }
        throw new IOException("Nessun commit su master prima del " + day);
    }

    /** I percorsi di tutti i file .java presenti nello snapshot, in ordine alfabetico. */
    public List<String> javaFiles(Snapshot snapshot) throws IOException {
        List<String> files = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository);
             TreeWalk treeWalk = new TreeWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(snapshot.commitId()));
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(JAVA));
            while (treeWalk.next()) {
                files.add(treeWalk.getPathString());
            }
        }
        files.sort(String::compareTo);
        return files;
    }

    /** Le rinomine e gli spostamenti di file .java tra due snapshot (percorso vecchio → nuovo). */
    public Map<String, String> renamesBetween(Snapshot from, Snapshot to) throws IOException {
        Map<String, String> renames = new HashMap<>();
        try (RevWalk walk = new RevWalk(repository);
             DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            RevCommit a = walk.parseCommit(repository.resolve(from.commitId()));
            RevCommit b = walk.parseCommit(repository.resolve(to.commitId()));
            diff.setRepository(repository);
            diff.setDetectRenames(true);
            diff.getRenameDetector().setRenameLimit(RENAME_LIMIT);
            diff.setPathFilter(PathSuffixFilter.create(JAVA));
            for (DiffEntry entry : diff.scan(a.getTree(), b.getTree())) {
                if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
                    renames.put(entry.getOldPath(), entry.getNewPath());
                }
            }
        }
        return renames;
    }

    private static LocalDate dayOf(RevCommit commit) {
        return LocalDate.ofInstant(Instant.ofEpochSecond(commit.getCommitTime()), ZoneOffset.UTC);
    }

    @Override
    public void close() {
        repository.close();
    }
}