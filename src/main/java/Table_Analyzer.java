import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created on 17/10/2016.
 *
 * @author Maximilian Maske
 */
class Table_Analyzer extends Spheroid_RGB {

    private long numberOfPixelsAboveThres;

    void runCountAndMean() {
        //create Results table
        ResultsTable resultsTable = Analyzer.getResultsTable();
        if (resultsTable == null) {
            resultsTable = new ResultsTable();
            Analyzer.setResultsTable(resultsTable);
        }

        HashMap<ImagePlus, ImageProcessor> channel = initChannelMap();

        //count cells and get meanPeak intensity from selected channels for each WiseRoi from WiseRoi Manager
        ImageStatistics imageStats;
        Calibration calibration = image.getCalibration();
        for (Roi currRoi : roiManager.getRoisAsArray()) {
            resultsTable.incrementCounter();
            resultsTable.addValue("ROI", currRoi.getName());
            for (ImagePlus currChannel : channel.keySet()) {
                currChannel.setRoi(currRoi);

                ArrayList<Point> peaks = rumNucleiCounter(currChannel, channel.get(currChannel));
                resultsTable.addValue("count (" + currChannel.getTitle() + ")", peaks.size());

                double mean = meanPeak((byte[])currChannel.getProcessor().getPixels(), peaks);
                resultsTable.addValue("mean peak (" + currChannel.getTitle() + ")", mean);

                double thresholdMean = mean(currRoi.getImage().getProcessor().convertToByteProcessor());
                resultsTable.addValue("mean (" + currChannel.getTitle() + ")", thresholdMean);
            }

            imageStats = ImageStatistics.getStatistics(currRoi.getImage().getProcessor(), Measurements.AREA, calibration);
            String unit = calibration.getUnit();
            if (unit.isEmpty()) unit = "pixel";
            resultsTable.addValue("total area (" + unit + ")", imageStats.area);

            //ratio
            if(channel.size() == 2) {
                int row = resultsTable.getCounter() - 1;
                resultsTable.addValue("count ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(1, row), resultsTable.getValueAsDouble(4, row)));
                resultsTable.addValue("mean peak ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(2, row), resultsTable.getValueAsDouble(5, row)));
                resultsTable.addValue("mean ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(3, row), resultsTable.getValueAsDouble(6, row)));
            } else if (channel.size() == 3) {
                int row = resultsTable.getCounter() - 1;

                String major = "(red)";
                String minor1 = "(green)";
                String minor2 = "(blue)";
                if (total == 1) {
                    major = "(green)";
                    minor1 = "(red)";
                }
                if (total == 2) {
                    major = "(blue)";
                    minor2 = "(red)";
                }

                //count ratioMinMax
                resultsTable.addValue("count " + minor1 + ":" + major
                        , (resultsTable.getValue("count " + minor1, row) / resultsTable.getValue("count " + major, row)) * 100);
                resultsTable.addValue("count " + minor2 + ":" + major
                        , (resultsTable.getValue("count " + minor2, row) / resultsTable.getValue("count " + major, row)) * 100);
                //intensity ratioMinMax
                resultsTable.addValue("intensity " + minor1 + ":" + major
                        , (resultsTable.getValue("mean " + minor1, row) / resultsTable.getValue("mean " + major, row)) * 100);
                resultsTable.addValue("intensity " + minor2 + ":" + major
                        , (resultsTable.getValue("mean " + minor2, row) / resultsTable.getValue("mean " + major, row)) * 100);

            }

            resultsTable.addResults();
            resultsTable.updateResults();
        }

        //Create and collect result images
        ArrayList<ImagePlus> resultImages = new ArrayList<>();
        for (ImagePlus currChannel : channel.keySet()) {
            resultImages.add(new ImagePlus("Results " + currChannel.getTitle(), channel.get(currChannel)));
        }

        for (ImagePlus currImage : resultImages) {
            currImage.show();
        }

        roiManager.runCommand(image, "Show All");
    }

    void runMean() {
        //create Results table
        ResultsTable resultsTable = Analyzer.getResultsTable();
        if (resultsTable == null) {
            resultsTable = new ResultsTable();
            Analyzer.setResultsTable(resultsTable);
        }

        ArrayList<ImagePlus> channel = new ArrayList<>();

        if (takeR) channel.add(rgb[0]);
        if (takeG) channel.add(rgb[1]);
        if (takeB) channel.add(rgb[2]);

        //count cells and get meanPeak intensity from selected channels for each WiseRoi from WiseRoi Manager
        ImageStatistics imageStats;
        Calibration calibration = image.getCalibration();
        for (Roi currRoi : roiManager.getRoisAsArray()) {
            resultsTable.incrementCounter();
            resultsTable.addValue("ROI", currRoi.getName());
            for (ImagePlus currChannel : channel) {
                currChannel.setRoi(currRoi);
                double thresholdMean = mean(currRoi.getImage().getProcessor().convertToByteProcessor());
                resultsTable.addValue("mean (" + currChannel.getTitle() + ")", thresholdMean);
                resultsTable.addValue("area (" + currChannel.getTitle() + ") within threshold", numberOfPixelsAboveThres);
                resultsTable.addValue("integrated density (" + currChannel.getTitle() + ")", thresholdMean * numberOfPixelsAboveThres);

            }

            imageStats = ImageStatistics.getStatistics(currRoi.getImage().getProcessor(), Measurements.AREA, calibration);
            String unit = calibration.getUnit();
            if (unit.isEmpty()) unit = "pixel";
            resultsTable.addValue("total area (" + unit + ")", imageStats.area);

            //ratio
            if(channel.size() == 2) {
                int row = resultsTable.getCounter() - 1;
                resultsTable.addValue("mean ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(1, row), resultsTable.getValueAsDouble(4, row)));

                ByteProcessor ip1 = channel.get(0).getProcessor().convertToByteProcessor();
                ByteProcessor ip2 = channel.get(1).getProcessor().convertToByteProcessor();
                resultsTable.addValue("ratio mean NEW (%)", ratioMean(ip1, ip2));

                resultsTable.addValue("area fraction (%)", ratioMinMax(resultsTable.getValueAsDouble(2, row), resultsTable.getValueAsDouble(5, row)));
            } else if (channel.size() == 3) {
                int row = resultsTable.getCounter() - 1;

                String major = "(red)";
                String minor1 = "(green)";
                String minor2 = "(blue)";
                if (total == 1) {
                    major = "(green)";
                    minor1 = "(red)";
                }
                if (total == 2) {
                    major = "(blue)";
                    minor2 = "(red)";
                }

                //intensity ratioMinMax
                resultsTable.addValue("intensity " + minor1 + ":" + major
                        , (resultsTable.getValue("mean " + minor1, row) / resultsTable.getValue("mean " + major, row)) * 100);
                resultsTable.addValue("intensity " + minor2 + ":" + major
                        , (resultsTable.getValue("mean " + minor2, row) / resultsTable.getValue("mean " + major, row)) * 100);

            }

            resultsTable.addResults();
            resultsTable.updateResults();
        }

        roiManager.runCommand(image, "Show All");
    }

    /**
     * Every Channel = 8bit ImagePlus and points to an RGB ImageProcessor for the result image
     */
    private HashMap<ImagePlus, ImageProcessor> initChannelMap() {
        HashMap<ImagePlus, ImageProcessor> channel = new HashMap<>();
        if(imageIsGray) {
            ImageProcessor results = (image.getProcessor().duplicate()).convertToRGB();
            channel.put(image, results);
        } else {
            if (takeR) {
                ImageProcessor redResults = (rgb[0].getProcessor().duplicate()).convertToRGB();
                channel.put(rgb[0], redResults);
            }
            if (takeG) {
                ImageProcessor greenResults = (rgb[1].getProcessor().duplicate()).convertToRGB();
                channel.put(rgb[1], greenResults);
            }
            if (takeB) {
                ImageProcessor blueResults = (rgb[2].getProcessor().duplicate()).convertToRGB();
                channel.put(rgb[2], blueResults);
            }
        }
        return channel;
    }

    /**
     * @param c1 component 1
     * @param c2 component 2
     * @return percentage with assumption that grater value is 100%
     */
    private double ratioMinMax(double c1, double c2) {
        return (Math.min(c1,c2) / Math.max(c1, c2)) * 100;
    }

    private ArrayList<Point> rumNucleiCounter(ImagePlus imp, ImageProcessor ipResults) {
        //min distance = cell width / 2 as recommended AND maskImp = null (ROI)
        Nuclei_Counter nucleiCounter = new Nuclei_Counter(imp, cellWidth, minDist, doubleThreshold , darkPeaks, null);
        nucleiCounter.run();
        ArrayList<Point> peaks = nucleiCounter.getPeaks();

        drawPeaks(imp, ipResults, peaks);

        return peaks;
    }

    private void drawPeaks(ImagePlus imp, ImageProcessor ipResults, ArrayList<Point> peaks) {
        ipResults.setColor(PEAKS_COLOR);
        ipResults.setLineWidth(1);

        for (Point p : peaks) {
            ipResults.drawDot(p.x, p.y);
//            System.out.println("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
        }

        ipResults.setColor(ROI_COLOR);
        imp.getRoi().drawPixels(ipResults);
    }

    private double meanPeak(byte[] pixels, ArrayList<Point> peaks) {
        double sum = 0;
        int width = image.getWidth();
        for (Point p : peaks) {
            int pos = p.y * width + p.x;
            sum += pixels[pos] & 0xff;
        }
        return sum / peaks.size();
    }


    // bug in this method: different results on same roi when varying count
    private double meanWithThreshold (ImageProcessor ip) {
        int[] histogram = ip.getHistogram();
        numberOfPixelsAboveThres = 0;
        double sum = 0;
        int minThreshold = 0;
        int maxThreshold= 255;

        if(darkPeaks) maxThreshold -= threshold;
        else minThreshold = threshold;

        for(int i = minThreshold; i <= maxThreshold; i++) {
            sum += (double)i * (double)histogram[i];
            numberOfPixelsAboveThres += (long)histogram[i];
        }

        return  sum / (double)numberOfPixelsAboveThres;
    }

    private double mean (ByteProcessor ip) {
        byte[] pix1 = (byte[]) ip.getPixels();
        int width = ip.getWidth();
        int height = ip.getHeight();
        Rectangle roi = ip.getRoi();
        long sum = 0;
        numberOfPixelsAboveThres = 0;

        int minThreshold = 0;
        int maxThreshold= 255;

        if(darkPeaks) maxThreshold -= threshold;
        else minThreshold = threshold;

        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                if (roi.contains(x,y)) {
                    int pos = y * width + x;
                    int value1 = pix1[pos] & 0xff;

                    if (value1 >= minThreshold && value1 <= maxThreshold) {
                        sum += value1;
                        numberOfPixelsAboveThres++;
                    }
                }
            }
        }

        return sum / (double) numberOfPixelsAboveThres;
    }

    private double ratioMean (ByteProcessor ip1, ByteProcessor ip2) {
        byte[] pix1 = (byte[]) ip1.getPixels();
        byte[] pix2 = (byte[]) ip2.getPixels();

        int width = ip1.getWidth();
        int height = ip1.getHeight();
        Rectangle roi = ip1.getRoi();

        long sum = 0;
        int numberOfPixels = 0;
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                if (roi.contains(x,y)) {
                    int pos = y * width + x;

                    int value1 = pix1[pos] & 0xff;
                    int value2 = pix2[pos] & 0xff;

//                    System.out.println(value1 + " : " + value2);

                    if(value2 > 0) sum += value1 / value2;
                    numberOfPixels++;
                }
            }

        }

        return sum / (double) numberOfPixels;
    }

}
