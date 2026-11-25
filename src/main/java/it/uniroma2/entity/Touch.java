package it.uniroma2.entity;

/**
 * Un commit che ha toccato una classe.
 *
 * @param inRelease true se il commit è nella finestra della release (nel tag k ma non nel tag k−1)
 */
public record Touch(CommitData commit, FileChange change, boolean inRelease) {
}