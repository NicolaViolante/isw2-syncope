package it.uniroma2.entity;

import java.util.List;
import java.util.Set;

/**
 * I commit di fix di un ticket bug e le classi del dataset che toccano.
 *
 * @param masterCommits commit di fix raggiungibili da master
 * @param branchCommits commit di fix presenti solo su altri rami (manutenzione)
 * @param classIds      Class ID delle classi del dataset toccate da almeno un commit di fix (master e rami)
 * @param otherFiles    file .java di produzione toccati dai fix che non corrispondono a classi del dataset
 *                      (per esempio i file del layout della 2.0)
 */
public record TicketFixes(String key, Category category, List<String> masterCommits, List<String> branchCommits,
                          Set<String> classIds, int otherFiles) {

    /** A = master e ramo; B = solo master; C = solo ramo di manutenzione; D = nessun commit. */
    public enum Category { A, B, C, D }

    public TicketFixes {
        masterCommits = List.copyOf(masterCommits);
        branchCommits = List.copyOf(branchCommits);
        classIds = Set.copyOf(classIds);
    }
}