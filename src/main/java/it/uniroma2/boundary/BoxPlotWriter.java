package it.uniroma2.boundary;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.BoxAndWhiskerCalculator;
import org.jfree.data.statistics.BoxAndWhiskerItem;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

/**
 * Disegna i box plot della Milestone 2 con JFreeChart, come Valentina: una figura per metrica, le configurazioni
 * sull'asse x, i classificatori come serie (un colore ciascuno). Ogni box riassume le 100 run della 10×10: mediana
 * (linea), quartili (box), baffi fino all'ultimo valore entro 1,5 volte lo scarto interquartile, valori oltre come
 * cerchi. La media non si disegna (JFreeChart la mette come un disco che copre la mediana): è nelle tabelle.
 */
public final class BoxPlotWriter {

    private static final int WIDTH = 1400;
    private static final int HEIGHT = 800;
    private static final Color[] COLORS = {new Color(0x1F77B4), new Color(0xFF7F0E), new Color(0x2CA02C),
            new Color(0x7F7F7F)};

    private BoxPlotWriter() {
    }

    /**
     * @param values serie (classificatore) → categoria (configurazione) → valori; l'ordine delle mappe è l'ordine
     *               del grafico
     * @param upper  limite superiore dell'asse y (1 per le metriche tra 0 e 1, 100 per NPofB20)
     */
    public static void write(String title, String valueLabel, Map<String, Map<String, List<Double>>> values,
                             double upper, Path file) throws IOException {
        System.setProperty("java.awt.headless", "true");
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        double lower = 0.0;
        for (Map.Entry<String, Map<String, List<Double>>> series : values.entrySet()) {
            for (Map.Entry<String, List<Double>> category : series.getValue().entrySet()) {
                if (!category.getValue().isEmpty()) {
                    dataset.add(allOutliersAsPoints(category.getValue()), series.getKey(), category.getKey());
                    lower = Math.min(lower, category.getValue().stream().mapToDouble(Double::doubleValue).min()
                            .orElse(0.0));
                }
            }
        }
        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(title, null, valueLabel, dataset, values.size() > 1);
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        NumberAxis axis = (NumberAxis) plot.getRangeAxis();
        axis.setRange(lower < 0 ? Math.floor(lower * 10) / 10 : 0.0, upper);
        CategoryAxis categories = plot.getDomainAxis();
        categories.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        categories.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        BoxAndWhiskerRenderer renderer = (BoxAndWhiskerRenderer) plot.getRenderer();
        renderer.setFillBox(true);
        renderer.setMeanVisible(false);
        renderer.setMedianVisible(true);
        renderer.setMaximumBarWidth(0.025);
        renderer.setDefaultToolTipGenerator(null);
        for (int i = 0; i < values.size(); i++) {
            renderer.setSeriesPaint(i, COLORS[i % COLORS.length]);
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        ChartUtils.saveChartAsPNG(file.toFile(), chart, WIDTH, HEIGHT);
    }

    /**
     * Le statistiche del box. JFreeChart non disegna i valori oltre 2 scarti interquartili ("far out") e mette al loro
     * posto un triangolo sul bordo del grafico, che sembra un valore vicino a 0 o a 1: qui ogni valore oltre i baffi
     * resta un cerchio nella sua posizione vera.
     */
    private static BoxAndWhiskerItem allOutliersAsPoints(List<Double> values) {
        BoxAndWhiskerItem item = BoxAndWhiskerCalculator.calculateBoxAndWhiskerStatistics(values);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        return new BoxAndWhiskerItem(item.getMean(), item.getMedian(), item.getQ1(), item.getQ3(),
                item.getMinRegularValue(), item.getMaxRegularValue(), min, max, item.getOutliers());
    }
}