package it.uniroma2.entity;

/** Le metriche di codice di una classe, calcolate sul file nello snapshot. */
public record CodeMetrics(int loc, int nom, int cyclomaticComplexity, int fanOut) {
}