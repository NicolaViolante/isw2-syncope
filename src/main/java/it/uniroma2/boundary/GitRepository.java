package it.uniroma2.boundary;

import it.uniroma2.entity.CommitData;
import it.uniroma2.entity.FileChange;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.Snapshot;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    /** I dati dei commit già letti: la stessa storia viene percorsa per ogni release. */
    private final Map<String, CommitData> commitCache = new HashMap<>();

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

    /**
     * La storia contenuta in uno snapshot: tutti i commit raggiungibili dal suo commit, esclusi i merge,
     * dal più recente al più vecchio (ordine topologico: un commit viene sempre prima dei suoi genitori).
     */
    public List<String> historyOf(Snapshot snapshot) throws IOException {
        List<String> ids = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.sort(RevSort.TOPO);
            walk.sort(RevSort.COMMIT_TIME_DESC, true);
            walk.setRevFilter(RevFilter.NO_MERGES);
            walk.markStart(walk.parseCommit(repository.resolve(snapshot.commitId())));
            for (RevCommit commit : walk) {
                ids.add(commit.getName());
            }
        }
        return ids;
    }

    /**
     * Autore, data, messaggio e modifiche di un commit, rispetto al primo genitore (il primo commit del
     * repository è confrontato con l'albero vuoto). Le rinomine sono riconosciute da Git.
     */
    public CommitData commitData(String id) throws IOException {
        CommitData cached = commitCache.get(id);
        if (cached != null) {
            return cached;
        }
        try (RevWalk walk = new RevWalk(repository);
             DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(id));
            RevTree parentTree = commit.getParentCount() == 0 ? null
                    : walk.parseCommit(commit.getParent(0)).getTree();
            diff.setRepository(repository);
            diff.setDiffComparator(RawTextComparator.DEFAULT);
            diff.setDetectRenames(true);
            diff.getRenameDetector().setRenameLimit(RENAME_LIMIT);
            List<DiffEntry> entries = diff.scan(parentTree, commit.getTree());
            List<FileChange> javaChanges = new ArrayList<>();
            for (DiffEntry entry : entries) {
                if (isJava(entry)) {
                    javaChanges.add(toChange(diff, entry));
                }
            }
            CommitData data = new CommitData(id, commit.getAuthorIdent().getEmailAddress(), dayOf(commit),
                    commit.getFullMessage(), entries.size(), javaChanges);
            commitCache.put(id, data);
            return data;
        }
    }

    private static boolean isJava(DiffEntry entry) {
        return entry.getNewPath().endsWith(JAVA) || entry.getOldPath().endsWith(JAVA);
    }

    private static FileChange toChange(DiffFormatter diff, DiffEntry entry) throws IOException {
        int added = 0;
        int deleted = 0;
        for (Edit edit : diff.toFileHeader(entry).toEditList()) {
            deleted += edit.getEndA() - edit.getBeginA();
            added += edit.getEndB() - edit.getBeginB();
        }
        FileChange.Kind kind = FileChange.Kind.valueOf(entry.getChangeType().name());
        String oldPath = kind == FileChange.Kind.ADD ? null : entry.getOldPath();
        String newPath = kind == FileChange.Kind.DELETE ? null : entry.getNewPath();
        return new FileChange(kind, oldPath, newPath, added, deleted);
    }

    /**
     * Il giorno del commit più vecchio, merge compresi, che ha modificato il file nella storia dello snapshot.
     * Serve per l'età delle classi create direttamente in un commit di merge, che le metriche escludono.
     */
    public LocalDate firstCommitDay(Snapshot snapshot, String path) throws IOException {
        LocalDate oldest = null;
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF));
            walk.markStart(walk.parseCommit(repository.resolve(snapshot.commitId())));
            for (RevCommit commit : walk) {
                LocalDate day = dayOf(commit);
                if (oldest == null || day.isBefore(oldest)) {
                    oldest = day;
                }
            }
        }
        return oldest;
    }

    /** Il testo di un file nello snapshot (UTF-8). */
    public String readFile(Snapshot snapshot, String path) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(snapshot.commitId()));
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                if (treeWalk == null) {
                    throw new IOException("File non presente nello snapshot " + snapshot.reference() + ": " + path);
                }
                byte[] bytes = repository.open(treeWalk.getObjectId(0)).getBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }

    private static LocalDate dayOf(RevCommit commit) {
        return LocalDate.ofInstant(Instant.ofEpochSecond(commit.getCommitTime()), ZoneOffset.UTC);
    }

    @Override
    public void close() {
        repository.close();
    }
}