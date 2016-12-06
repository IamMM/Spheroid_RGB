import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;

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

        ArrayList<ImagePlus> channels = initChannels();

        //image stats
        Calibration calibration = image.getCalibration();
        Roi[] roiArray = main.roiManager.getSelectedRoisAsArray();
        if(roiArray == null) roiArray = main.roiManager.getRoisAsArray();
        GenericDialog warning = new GenericDialog("More than 10 items in ROI Manager");
        warning.addMessage("Are you sure you want to analyze all Regions?");
        warning.setOKLabel("yes");
        warning.setCancelLabel("no");
        if(roiArray.length > 10) {
            warning.showDialog();
        }
        if(warning.wasOKed() || roiArray.length < 10) {
            for (Roi currRoi : roiArray) {
                LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();
                for (ImagePlus currChannel : channels) {
//                    ImageProcessor currIp = currChannel.getStack().getProcessor(currRoi.getPosition());
                    currChannel.setRoi(currRoi);
                    String title = currChannel.getTitle().toLowerCase();
                    threshold = main.getThreshold(title);
                    double thresholdMean = roiMean(currChannel, threshold);
                    if (countIsSelected) {
                        ArrayList<Point> peaks = rumNucleiCounter(currChannel, threshold);
                        resultValues.put("count (" + title + ")", (double) peaks.size());

                        double meanPeak = meanPeak((byte[]) currChannel.getProcessor().getPixels(), peaks);
                        resultValues.put("peaks mean (" + title + ")", meanPeak);

                        // "Density: cells per square (calibration.getUnit())
                        double density = (double) peaks.size()/calibration.getY(calibration.getX(totalNumberOfPixels));
                        resultValues.put("nuclei density (" + title + ")", density);

                        if (plotIsSelected) countDistanceFunction(currChannel.getTitle(), peaks, currRoi);
                    }

                    if (meanIsSelected) resultValues.put("mean (" + title + ")", thresholdMean);
                    if (areaIsSelected) {
                        resultValues.put("area (" + title + ")", calibration.getY(calibration.getX(numberOfPixelsAboveThreshold)));
                        resultValues.put("total area fraction (" + title + ")", numberOfPixelsAboveThreshold / (double) totalNumberOfPixels);
                    }
                    if (idIsSelected)
                        resultValues.put("integrated density (" + title + ")", thresholdMean * numberOfPixelsAboveThreshold);
                }

                if (areaIsSelected) {
                    if (calibration.scaled()) resultValues.put("total area (" + calibration.getUnit() + "²)",
                            calibration.getY(calibration.getX(totalNumberOfPixels)));
                    else resultValues.put("total area (number of pixels)", (double) totalNumberOfPixels);
                }

                // ratio values
                if (channels.size() >= 2) {
                    if (ratioValuesIsSelected && countIsSelected) {
                        resultValues.putAll(ratio(resultValues, "count", major));
                        resultValues.putAll(ratio(resultValues, "peaks mean", major));
                    }
                    if (ratioValuesIsSelected && meanIsSelected)
                        resultValues.putAll(ratio(resultValues, "mean", major));
                    if (ratioValuesIsSelected && areaIsSelected)
                        resultValues.putAll(ratio(resultValues, "area", major));
                    if (ratioMeanIsSelected) resultValues.put("ratio mean", roiMeanRatio(channels, major, threshold));
                }

                addValuesToResultsTable(image.getTitle(), currRoi.getName(), resultValues);
            }

            for (ImagePlus channel : channels) {
                channel.setTitle("Results " + channel.getTitle());
                channel.show("show results");
            }

            main.roiManager.runCommand(image, "Show All");
        }
    }

    private ArrayList<ImagePlus> initChannels() {
        ArrayList<ImagePlus> channels = new ArrayList<>();
        if (main.imageIsGray) channels.add(main.image);
        else {
            if (main.takeR) channels.add(main.rgb[0]);
            if (main.takeG) channels.add(main.rgb[1]);
            if (main.takeB) channels.add(main.rgb[2]);
        }
        return channels;
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

    private ArrayList<Point> rumNucleiCounter(ImagePlus imp, int threshold) {
        //maskImp = null (ROI)
        double doubleThreshold = 10 * ((double)threshold /255);
        Nuclei_Counter nucleiCounter = new Nuclei_Counter(imp, main.cellWidth, main.minDist, doubleThreshold , main.darkPeaks);
        nucleiCounter.run();
        ArrayList<Point> peaks = nucleiCounter.getPeaks();

        drawPeaks(imp, peaks);

        return peaks;
    }

    private void drawPeaks(ImagePlus imp, ArrayList<Point> peaks) {
        Overlay overlay = imp.getOverlay();
        if(overlay == null) overlay = new Overlay();

        for (Point p : peaks) {
            overlay.add(new PointRoi(p.x, p.y));
        }

        overlay.add(imp.getRoi());
        imp.killRoi();
        imp.setOverlay(overlay);
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

    private double roiMeanRatio(ArrayList<ImagePlus> channels, String majorTitle, int threshold) {
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
