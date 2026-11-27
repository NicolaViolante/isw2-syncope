package it.uniroma2.entity;

/**
 * Media, mediana e deviazione standard di una metrica su più run.
 *
 * @param runs run in cui la metrica è definita (0: mai definita, gli altri valori sono NaN)
 */
public record MetricSummary(int runs, double mean, double median, double std) {
}