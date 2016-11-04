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
import java.util.LinkedHashMap;

/**
 * Created on 17/10/2016.
 *
 * @author Maximilian Maske
 */
class Table_Analyzer extends Spheroid_RGB {

    private ResultsTable table;
    private long numberOfPixelsAboveThres;
    private long totalNumberOfPixels;

    void run (ImagePlus image, boolean[] options) {
        boolean countIsSelected = options[0];
        boolean meanIsSelected = options[1];
        boolean areaIsSelected = options[2];
        boolean idIsSelected = options[3]; //id = integrated density
        boolean cleanTable = options[4];

        HashMap<ImagePlus, ImageProcessor> channel = initChannelMap();

        //image stats
        Calibration calibration = image.getCalibration();
        for (Roi currRoi : roiManager.getRoisAsArray()) {
            LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();
            for (ImagePlus currChannel : channel.keySet()) {
                currChannel.setRoi(currRoi);

                if(countIsSelected) {
                    ArrayList<Point> peaks = rumNucleiCounter(currChannel, channel.get(currChannel));
                    resultValues.put("count (" + currChannel.getTitle() + ")", (double) peaks.size());

                    double meanPeak = meanPeak((byte[]) currChannel.getProcessor().getPixels(), peaks);
                    resultValues.put("mean peak (" + currChannel.getTitle() + ")", meanPeak);
                }

                double thresholdMean = roiMean(currChannel);
                if(meanIsSelected) resultValues.put("mean (" + currChannel.getTitle() + ")", thresholdMean);
                if(areaIsSelected) resultValues.put("number of pixels (" + currChannel.getTitle() + ")", (double) numberOfPixelsAboveThres);
                if(idIsSelected) resultValues.put("integrated density (" + currChannel.getTitle() + ")", thresholdMean * numberOfPixelsAboveThres);
            }

            if(areaIsSelected) {
                resultValues.put("total area (pixel)", (double) totalNumberOfPixels);
                if(calibration.scaled()) resultValues.put("total area (" + calibration.getUnit() + ")", calibration.getX(totalNumberOfPixels));
            }
            addValuesToResultsTable(image.getTitle(), currRoi.getName(), resultValues, cleanTable);
        }

        // show count result images
        if(countIsSelected) {
            for (ImagePlus currChannel : channel.keySet()) {
                new ImagePlus("Results " + currChannel.getTitle(), channel.get(currChannel)).show();
            }
        }

        roiManager.runCommand(image, "Show All");
    }

    private void addValuesToResultsTable(String imgTitle, String roiTitle, LinkedHashMap<String, Double> results, boolean cleanTable) {
        if (table==null || cleanTable) table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Image", imgTitle);
        table.addValue("ROI", roiTitle);
        for (String s : results.keySet()) {
            table.addValue(s, results.get(s));
        }

        table.show("Count and Mean Results");
    }


    void runCountAndMean() {
        //create Results table
        ResultsTable resultsTable = Analyzer.getResultsTable();
        if (resultsTable == null) {
            resultsTable = new ResultsTable();
            Analyzer.setResultsTable(resultsTable);
        }

        HashMap<ImagePlus, ImageProcessor> channel = initChannelMap();

        //count cells and get meanPeak intensity from selected channels for each Roi from Roi Manager
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

                double thresholdMean = roiMean(currChannel);
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
                double thresholdMean = roiMean(currChannel);
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

                resultsTable.addValue("ratio mean NEW (%)", roiMeanRatio(channel.get(0), channel.get(1)));

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


    // mean of all pixels in image
//    private double meanWithThreshold (ImageProcessor ip) {
//        int[] histogram = ip.getHistogram();
//        numberOfPixelsAboveThres = 0;
//        double sum = 0;
//        int minThreshold = 0;
//        int maxThreshold= 255;
//
//        if(darkPeaks) maxThreshold -= threshold;
//        else minThreshold = threshold;
//
//        for(int i = minThreshold; i <= maxThreshold; i++) {
//            sum += (double)i * (double)histogram[i];
//            numberOfPixelsAboveThres += (long)histogram[i];
//        }
//
//        for (int count : histogram) {
//            totalNumberOfPixels += count;
//        }
//
//        return  sum / (double)numberOfPixelsAboveThres;
//    }

    private double roiMean(ImagePlus imp) {
        Roi roi = imp.getRoi();
        if (roi!=null && !roi.isArea()) roi = null;
        ImageProcessor ip = imp.getProcessor();
        ImageProcessor mask = roi!=null?roi.getMask():null;
        Rectangle r = roi!=null ? roi.getBounds() : new Rectangle(0,0,ip.getWidth(),ip.getHeight());

        double sum = 0;
        numberOfPixelsAboveThres = 0;
        totalNumberOfPixels = 0;

        int minThreshold = 0;
        int maxThreshold= 255;

        if(darkPeaks) maxThreshold -= threshold;
        else minThreshold = threshold;

        for (int y=0; y<r.height; y++) {
            for (int x=0; x<r.width; x++) {
                if (mask==null||mask.getPixel(x,y)!=0) {
                    totalNumberOfPixels++;
                    float value = ip.getPixelValue(x+r.x, y+r.y);
                    if (value >= minThreshold && value <= maxThreshold) {
                        sum += value;
                        numberOfPixelsAboveThres++;
                    }
                }
            }
        }
        return sum/numberOfPixelsAboveThres;
    }

    private double roiMeanRatio(ImagePlus imp1, ImagePlus imp2) {
        Roi roi = imp1.getRoi();
        if (roi!=null && !roi.isArea()) roi = null;
        ImageProcessor ip1 = imp1.getProcessor();
        ImageProcessor ip2 = imp2.getProcessor();
        ImageProcessor mask = roi!=null?roi.getMask():null;
        Rectangle r = roi!=null ? roi.getBounds() : new Rectangle(0,0,ip1.getWidth(),ip1.getHeight());

        double sum = 0;
        int count = 0;

        int minThreshold = 0;
        int maxThreshold= 255;

        if(darkPeaks) maxThreshold -= threshold;
        else minThreshold = threshold;

        for (int y=0; y<r.height; y++) {
            for (int x=0; x<r.width; x++) {
                if (mask==null||mask.getPixel(x,y)!=0) {

                    float value1 = ip1.getPixelValue(x+r.x, y+r.y);
                    float value2 = ip2.getPixelValue(x+r.x, y+r.y);

                    if (value1 >= minThreshold && value1 <= maxThreshold && value2 > minThreshold && value2 <= maxThreshold) {
                        sum += value1 / value2;
                        count++;
                    }
                }
            }
        }
        return sum/count;
    }

}
