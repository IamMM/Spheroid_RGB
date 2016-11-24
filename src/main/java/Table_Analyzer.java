import ij.ImagePlus;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Created on 17/10/2016.
 *
 * @author Maximilian Maske
 */
class Table_Analyzer {

    private Spheroid_RGB main;
    private ResultsTable table;
    private long numberOfPixelsAboveThreshold;
    private long totalNumberOfPixels;

    void run (Spheroid_RGB main, ImagePlus image, boolean[] options, String major) {
        this.main = main;

        boolean cleanTable = options[0];
        boolean countIsSelected = options[1];
        boolean meanIsSelected = options[2];
        boolean areaIsSelected = options[3];
        boolean idIsSelected = options[4]; //id = integrated density
        boolean ratioMeanIsSelected = options[5];
        boolean ratioValuesIsSelected = options[6];
        boolean plotIsSelected = options[7];

        major = major.toLowerCase();

        int threshold = 0;

        if(cleanTable) table = new ResultsTable();

        LinkedHashMap<ImagePlus, ImageProcessor> channel = initChannelMap();

        //image stats
        Calibration calibration = image.getCalibration();
        for (Roi currRoi : main.roiManager.getRoisAsArray()) {
            LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();
            for (ImagePlus currChannel : channel.keySet()) {
                currChannel.setRoi(currRoi);
                String title = currChannel.getTitle().toLowerCase();
                threshold = main.getThreshold(title);
                if(countIsSelected) {
                    ArrayList<Point> peaks = rumNucleiCounter(currChannel, channel.get(currChannel), threshold);
                    resultValues.put("count (" + title + ")", (double) peaks.size());

                    double meanPeak = meanPeak((byte[]) currChannel.getProcessor().getPixels(), peaks);
                    resultValues.put("peaks mean (" + title + ")", meanPeak);

                    if(plotIsSelected) countDistanceFunction(currChannel.getTitle(), peaks, currRoi);
                }

                double thresholdMean = roiMean(currChannel, threshold);
                if(meanIsSelected) resultValues.put("mean (" + title + ")", thresholdMean);
                if(areaIsSelected) {
                    resultValues.put("area (" + title + ")", calibration.getY(calibration.getX(numberOfPixelsAboveThreshold)));
                    resultValues.put("total area fraction (" + title + ")", numberOfPixelsAboveThreshold/(double)totalNumberOfPixels);
                }
                if(idIsSelected) resultValues.put("integrated density (" + title + ")", thresholdMean * numberOfPixelsAboveThreshold);
            }

            if(areaIsSelected) {
                if(calibration.scaled()) resultValues.put("total area (" + calibration.getUnit() + "²)",
                        calibration.getY(calibration.getX(totalNumberOfPixels)));
                else resultValues.put("total area (number of pixels)", (double) totalNumberOfPixels);
            }

            // ratio values
            if(channel.size() >= 2) {
                if (ratioValuesIsSelected && countIsSelected) {
                    resultValues.putAll(ratio(resultValues, "count", major));
                    resultValues.putAll(ratio(resultValues, "peaks mean", major));
                }
                if (ratioValuesIsSelected && meanIsSelected) resultValues.putAll(ratio(resultValues, "mean", major));
                if (ratioValuesIsSelected && areaIsSelected) resultValues.putAll(ratio(resultValues, "area", major));
                if (ratioMeanIsSelected) resultValues.put("ratio mean", roiMeanRatio(channel.keySet(), major, threshold));
            }

            addValuesToResultsTable(image.getTitle(), currRoi.getName(), resultValues);
        }

        // show count result images
        if(countIsSelected) {
            for (ImagePlus currChannel : channel.keySet()) {
                new ImagePlus("Results " + currChannel.getTitle(), channel.get(currChannel)).show();
            }
        }

        main.roiManager.runCommand(image, "Show All");
    }

    private LinkedHashMap<String, Double> ratio(LinkedHashMap<String, Double> resultValues, String key, String major) {
        LinkedHashMap<String, Double> ratioValues = new LinkedHashMap<>();

        String majorHeading = key + " (" + major + ")";
        if (resultValues.get(majorHeading) == null) return ratioValues;

        double majorValue = resultValues.get(majorHeading);
        for (String heading : resultValues.keySet()) {
            if (heading.startsWith(key) && !heading.contains(major)) {
                double ratio = resultValues.get(heading) / majorValue;
                ratioValues.put(heading + ":" + majorHeading, ratio);
            }
        }
        return ratioValues;
    }

    private void addValuesToResultsTable(String imgTitle, String roiTitle, LinkedHashMap<String, Double> results) {
        if (table==null) table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Image", imgTitle);
        table.addValue("ROI", roiTitle);
        for (String s : results.keySet()) {
            table.addValue(s, results.get(s));
        }

        table.show("Count and Mean Results");
    }

    /**
     * Every Channel = 8bit ImagePlus and points to an RGB ImageProcessor for the result image
     */
    private LinkedHashMap<ImagePlus, ImageProcessor> initChannelMap() {
        LinkedHashMap<ImagePlus, ImageProcessor> channel = new LinkedHashMap<>();
        if(main.imageIsGray) {
            ImageProcessor results = (main.image.getProcessor().duplicate()).convertToRGB();
            channel.put(main.image, results);
        } else {
            if (main.takeR) {
                ImageProcessor redResults = (main.rgb[0].getProcessor().duplicate()).convertToRGB();
                channel.put(main.rgb[0], redResults);
            }
            if (main.takeG) {
                ImageProcessor greenResults = (main.rgb[1].getProcessor().duplicate()).convertToRGB();
                channel.put(main.rgb[1], greenResults);
            }
            if (main.takeB) {
                ImageProcessor blueResults = (main.rgb[2].getProcessor().duplicate()).convertToRGB();
                channel.put(main.rgb[2], blueResults);
            }
        }
        return channel;
    }

    private ArrayList<Point> rumNucleiCounter(ImagePlus imp, ImageProcessor ipResults, int threshold) {
        //maskImp = null (ROI)
        double doubleThreshold = 10 * ((double)threshold /255);
        Nuclei_Counter nucleiCounter = new Nuclei_Counter(imp, main.cellWidth, main.minDist, doubleThreshold , main.darkPeaks, null);
        nucleiCounter.run();
        ArrayList<Point> peaks = nucleiCounter.getPeaks();

        drawPeaks(imp, ipResults, peaks);

        return peaks;
    }

    private void drawPeaks(ImagePlus imp, ImageProcessor ipResults, ArrayList<Point> peaks) {
        ipResults.setColor(main.PEAKS_COLOR);
        ipResults.setLineWidth(1);

        for (Point p : peaks) {
            ipResults.drawDot(p.x, p.y);
//            System.out.println("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
        }

        ipResults.setColor(main.ROI_COLOR);
        imp.getRoi().drawPixels(ipResults);
    }

    private double meanPeak(byte[] pixels, ArrayList<Point> peaks) {
        double sum = 0;
        int width = main.image.getWidth();
        for (Point p : peaks) {
            int pos = p.y * width + p.x;
            sum += pixels[pos] & 0xff;
        }
        return sum / peaks.size();
    }


    // mean of all pixels in image
//    private double meanWithThreshold (ImageProcessor ip) {
//        int[] histogram = ip.getHistogram();
//        numberOfPixelsAboveThreshold = 0;
//        double sum = 0;
//        int minThreshold = 0;
//        int maxThreshold= 255;
//
//        if(darkPeaks) maxThreshold -= threshold;
//        else minThreshold = threshold;
//
//        for(int i = minThreshold; i <= maxThreshold; i++) {
//            sum += (double)i * (double)histogram[i];
//            numberOfPixelsAboveThreshold += (long)histogram[i];
//        }
//
//        for (int count : histogram) {
//            totalNumberOfPixels += count;
//        }
//
//        return  sum / (double)numberOfPixelsAboveThreshold;
//    }

    private double roiMean(ImagePlus imp, int threshold) {
        Roi roi = imp.getRoi();
        if (roi!=null && !roi.isArea()) roi = null;
        ImageProcessor ip = imp.getProcessor();
        ImageProcessor mask = roi!=null?roi.getMask():null;
        Rectangle r = roi!=null ? roi.getBounds() : new Rectangle(0,0,ip.getWidth(),ip.getHeight());

        double sum = 0;
        numberOfPixelsAboveThreshold = 0;
        totalNumberOfPixels = 0;

        int minThreshold = 0;
        int maxThreshold= 255;

        if(main.darkPeaks) maxThreshold -= threshold;
        else minThreshold = threshold;

        for (int y=0; y<r.height; y++) {
            for (int x=0; x<r.width; x++) {
                if (mask==null||mask.getPixel(x,y)!=0) {
                    totalNumberOfPixels++;
                    float value = ip.getPixelValue(x+r.x, y+r.y);
                    if (value >= minThreshold && value <= maxThreshold) {
                        sum += value;
                        numberOfPixelsAboveThreshold++;
                    }
                }
            }
        }
        return sum/ numberOfPixelsAboveThreshold;
    }

    private double roiMeanRatio(Set<ImagePlus> channels, String majorTitle, int threshold) {
        if(channels.size() < 2) return 0;
        ImagePlus[] impArray = new ImagePlus[channels.size()];
        channels.toArray(impArray);
        ImagePlus minor_imp;
        ImagePlus major_imp;
        if( impArray[0].getTitle().equalsIgnoreCase(majorTitle)) {
            major_imp = impArray[0];
            minor_imp = impArray[1];
        } else {
            major_imp = impArray[1];
            minor_imp = impArray[0];
        }

        Roi roi = major_imp.getRoi();
        if (roi!=null && !roi.isArea()) roi = null;
        ImageProcessor minor_ip = minor_imp.getProcessor();
        ImageProcessor major_ip = major_imp.getProcessor();
        ImageProcessor mask = roi!=null ? roi.getMask() : null;
        Rectangle r = roi!=null ? roi.getBounds() : new Rectangle(0,0,minor_ip.getWidth(),minor_ip.getHeight());

        double sum = 0;
        int count = 0;

        int minThreshold = 1;
        int maxThreshold = 255;

        if(main.darkPeaks) maxThreshold -= threshold;
        else if(threshold > 0) minThreshold = threshold;

        for (int y=0; y<r.height; y++) {
            for (int x=0; x<r.width; x++) {
                if (mask==null||mask.getPixel(x,y)!=0) {

                    float value1 = minor_ip.getPixelValue(x+r.x, y+r.y);
                    float value2 = major_ip.getPixelValue(x+r.x, y+r.y);

                    if (value1 >= minThreshold && value1 <= maxThreshold && value2 >= minThreshold && value2 <= maxThreshold) {
                        sum += value1 / value2;
                        count++;
                    }
                }
            }
        }
        return sum/count;
    }

    private void countDistanceFunction(String title, ArrayList<Point> peaks, Roi roi) {
        int quantification = main.quantification;

        // find centroid from roi
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        double xCentroid = stats.xCentroid;
        double yCentroid = stats.yCentroid;
        Calibration calibration = roi.getImage().getCalibration();
        if (calibration.scaled()) {
            xCentroid = calibration.getRawX(xCentroid);
            yCentroid = calibration.getRawY(yCentroid);
        }

        // measure distances from each point to centroid
        int height = (int) (roi.getBounds().getHeight());
        int width = (int) (roi.getBounds().getWidth());
        int bounds = (int) (Math.sqrt(height*height + width*width) + 1);
        double[] count = new double[bounds / quantification];

        int maxDistance = 0;
        for (Point p : peaks) {
            double a2 = (xCentroid - p.x) *(xCentroid - p.x);
            double b2 = (yCentroid - p.y) * (yCentroid - p.y);
            int distance = (int) Math.round(Math.sqrt(a2 + b2));
            distance = distance / quantification;
            count[distance]++;
            maxDistance = distance > maxDistance ? distance : maxDistance;
        }

        // init x and y coordinates and find count max
        double[] x = new double[maxDistance];
        double[] y = new double[maxDistance];
        double countMax = 0;
        for (int i = 0; i < maxDistance; i++) {
            double c = count[i];
            x[i] = i * quantification;
            y[i] = c;
            countMax = c > countMax ?  c : countMax;
        }

        // plot
        Plot plot = new Plot("Count Distance " + title + " | ROI: " + roi.getName() + "  | Quantification factor: " + quantification,"Distance from centroid (pixels)","Count");
        plot.setLimits(0, maxDistance*quantification, 0, countMax);
        plot.addPoints(x, y, Plot.LINE);
        plot.show();
    }
}
