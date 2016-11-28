import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.RoiRotator;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Created on 14/10/2016.
 *
 * @author Maximilian Maske
 */
class Multi_Plot{

    private ImagePlus mask;
    private Roi roi;
    private double yCentroid;
    private double xCentroid;
    private ArrayList<Roi> lines;
    private int numberOfProfiles;
    private double yMax;
    private int xMax;
    private String plotTitle;
    private ResultsTable table;

    void run(ArrayList<ImagePlus> channel, ImagePlus mask, int numberOfProfiles, boolean radius, int profileLength, int customYMax, boolean[] options) {
        yMax = 0;
        xMax = 0;

        this.mask = mask;
        this.roi = mask.getRoi();
        this.numberOfProfiles = numberOfProfiles;

        plotTitle = "Star plot " + mask.getTitle();

        // options
        boolean cleanTable = options[0];
        boolean showLines = options[1];
        boolean showChannel = options[2];
        boolean plotAll = options[3];
        boolean autoScale = options[4];

        if(cleanTable) table = new ResultsTable();

        initCentroid();
        initLines(radius, getRadius(roi, profileLength));
        if (showLines) showLines(mask);
        else mask.setOverlay(null);
        if (showChannel) showLines(channel);
        HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles = createAllPlots(channel);
        if(!autoScale) yMax = customYMax;
        plotStarAverage(listOfAllProfiles, plotAll, radius);
    }

    void runRingPlot(ArrayList<ImagePlus> channel, ImagePlus mask, int customYMax, boolean[] options, int profileLength) {
        yMax = 0;
        xMax = 0;

        this.mask = mask;
        this.roi = mask.getRoi();

        plotTitle = "Ring plot " + mask.getTitle();

        // options
        boolean cleanTable = options[0];
        boolean showRings = options[1];
        boolean showChannel = options[2];
        boolean autoScale = options[4];

        if(cleanTable) table = new ResultsTable();
        if(!autoScale) yMax = customYMax;

        initCentroid();
        int radius = getRadius(roi, profileLength);
        plotDistance(channel, radius, showChannel, true);
        if(showRings) showOuterRingAndCentroid(mask, radius);

    }

    void runConvexHullPlot(ArrayList<ImagePlus> channel, ImagePlus mask, int customYMax, boolean[] options, int profileLength) {
        yMax = 0;
        xMax = 0;

        this.roi = mask.getRoi();

        plotTitle = "Convex hull plot " + mask.getTitle();

        // options
        boolean cleanTable = options[0];
        boolean showConvexHull = options[1];
        boolean showChannel = options[2];
        boolean autoScale = options[4];

        if(cleanTable) table = new ResultsTable();
        if(!autoScale) yMax = customYMax;

        initCentroid();
        int radius = getRadius(roi, profileLength);
        plotDistance(channel, radius, showChannel, false);
        if (showConvexHull) mask.setOverlay(new Overlay(new PolygonRoi(roi.getConvexHull(),Roi.POLYGON)));

    }

    private Color toColor(String color) {
        switch (color) {
            case "red": return Color.red;
            case "green": return Color.green;
            case "blue": return Color.blue;
            default: return Color.black;
        }
    }

    private void initCentroid() {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        xCentroid = stats.xCentroid;
        yCentroid = stats.yCentroid;
        Calibration calibration = roi.getImage().getCalibration();
        if (calibration.scaled()) {
            xCentroid = calibration.getRawX(xCentroid);
            yCentroid = calibration.getRawY(yCentroid);
        }
    }

    private void initLines(boolean radiusMode, int radius) {
        lines =  new ArrayList<>();
        double angle;
        if (radiusMode) {
            double newAngle = 0;
            angle = 360 / (double) numberOfProfiles;
            for (int i = 0; i <numberOfProfiles;i++) {
                double deltaX = Math.cos(Math.toRadians(newAngle)) * radius;
                double deltaY = Math.sin(Math.toRadians(newAngle)) * radius;
                lines.add(new Line(xCentroid, yCentroid, xCentroid + deltaX, yCentroid + deltaY));
                newAngle += angle;
            }
        } else {
            angle = 180 / (double) numberOfProfiles;
            Roi horizontal = new Line(xCentroid - radius, yCentroid, xCentroid + radius, yCentroid);
            for (int i = 0; i <numberOfProfiles;i++) {
                horizontal = RoiRotator.rotate(horizontal, angle);
                lines.add(horizontal);
            }
        }
    }

    private void showLines(ImagePlus image) {
        Overlay overlay = new Overlay();
        for (Roi l : lines) {
            overlay.add(l);
        }

        image.setOverlay(overlay);
        image.show();
    }

    private void showLines(ArrayList<ImagePlus> channel) {
        for (ImagePlus currChannel : channel) {
            Overlay overlay = new Overlay();
            for (Roi l : lines) {
                overlay.add(l);
            }

            currChannel.setOverlay(overlay);
            currChannel.show();
        }
    }

    private HashMap<ImagePlus , ArrayList<double[]>> createAllPlots(ArrayList<ImagePlus> channel) {
        HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles = new HashMap<>();
        for (ImagePlus currChannel : channel) {
            ArrayList<double[]> profiles = new ArrayList<>();
            for (Roi l : lines) {
                currChannel.setRoi(l);
                ProfilePlot profilePlot = new ProfilePlot(currChannel);
                profiles.add(profilePlot.getProfile());
                if(profilePlot.getMax() > yMax) {
                    yMax = profilePlot.getMax();
                }
                int length = profilePlot.getProfile().length;
                if(length > xMax) xMax = length;
                currChannel.setRoi(roi);
            }
            listOfAllProfiles.put(currChannel, profiles);
        }
        return listOfAllProfiles;
    }

    private double[] avgProfile(ArrayList<double[]> profiles) {
        double[] avg = new double[profiles.get(0).length];
        for (double[] profile : profiles) {
            for (int i = 0; i < profile.length; i++) {
                avg[i] += profile[i];
            }
        }

        int size = profiles.size();
        for (int i = 0; i < avg.length; i++) {
            avg[i] /= size;
        }

        return avg;
    }

    private void plotStarAverage(HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles, boolean plotAll, boolean radius) {
        // init x values 0 .. xMax
        double[] x = new double[xMax];
        for (int i = 0; i < xMax; i++) {
            x[i] = (double) i;
        }

        PlotWindow.noGridLines = false; // draw grid lines
        Plot plot = new Plot(plotTitle,"Distance (pixels)","Intensity (gray value)");
        plot.setLimits(0, xMax, 0, yMax);

        // avg all profiles of all channels
        LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();

        for (ImagePlus currChannel :listOfAllProfiles.keySet()){
            // all plots
            ArrayList<double[]> profiles = listOfAllProfiles.get(currChannel);
            if(plotAll) {
                plot.setLineWidth(1);
                plot.setColor(Color.gray);
                for (double[] y : profiles) {
                    plot.addPoints(x, y, PlotWindow.LINE);
                }
            }

            //average plot
            double[] avg = avgProfile(profiles);
            Color avgColor = toColor(currChannel.getTitle());
            plot.setColor(avgColor);
            plot.setLineWidth(2);
            plot.addPoints(x, avg, PlotWindow.LINE);

            if (radius) {
                double[] max = getMaxCoordinates(avg);
                resultValues.put(currChannel.getTitle() + " max x", max[0]);
                resultValues.put(currChannel.getTitle() + " max y", max[1]);

                double[] bounds = getRightGradientChange(avg);
                resultValues.put(currChannel.getTitle() + " bounds x", bounds[0]);
                resultValues.put(currChannel.getTitle() + " bounds y", bounds[1]);
                plot.setColor(Color.darkGray);
                plot.setLineWidth(1);
                plot.drawLine(bounds[0], 0, bounds[0], yMax);

                double area = getArea(avg, bounds[0]);
                resultValues.put(currChannel.getTitle() + " area", area);

                plot.setLineWidth(1);
                plot.setColor(Color.darkGray);
                plot.drawDottedLine(0, max[1], max[0], max[1], 2);
            }
        }

        if (radius) addValuesToResultsTable(resultValues);

        plot.show();
        mask.setRoi(roi);
    }

    private void plotDistance(ArrayList<ImagePlus> channel, int radius, boolean showChannel, boolean ring) {
         // init x values 0 .. xMax
        double[] x = new double[radius];
        for (int i = 0; i < radius; i++) {
            x[i] = (double) i;
        }

        String xLabel = ring ? "Distance from centroid (pixels)": "Distance from surface edge (pixels)";

        PlotWindow.noGridLines = false; // draw grid lines
        Plot plot = new Plot(plotTitle,xLabel,"Intensity (gray value)");
        plot.setLimits(0, radius, 0, 255);

        // ring plot
        LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();
        for (ImagePlus currChannel : channel) {
            double[] y = ring ?
                    getRingValues(currChannel, radius, showChannel) : getConvexHullValues(currChannel, roi, radius);
            plot.setColor(toColor(currChannel.getTitle()));
            plot.setLineWidth(2);
            plot.addPoints(x, y,PlotWindow.LINE);

            double[] max = getMaxCoordinates(y);
            resultValues.put(currChannel.getTitle() + " max x", max[0]);
            resultValues.put(currChannel.getTitle() + " max y", max[1]);

            double[] bounds = getRightGradientChange(y);
            resultValues.put(currChannel.getTitle() + " bounds x", bounds[0]);
            resultValues.put(currChannel.getTitle() + " bounds y", bounds[1]);
            plot.setColor(Color.darkGray);
            plot.setLineWidth(1);
            plot.drawLine(bounds[0], 0, bounds[0], 255);

            double area = getArea(y, bounds[0]);
            resultValues.put(currChannel.getTitle() + " area", area);

            plot.setLineWidth(1);
            plot.setColor(Color.darkGray);
            plot.drawDottedLine(0, max[1], max[0], max[1], 2);
        }
        addValuesToResultsTable(resultValues);
        plot.show();
    }

    private void showOuterRingAndCentroid(ImagePlus src, int radius) {
        Overlay overlay= new Overlay();
        overlay.add(new PointRoi(xCentroid,yCentroid));
        overlay.add(new OvalRoi(xCentroid - radius,yCentroid-radius,radius<<1,radius<<1));
        src.setOverlay(overlay);
    }

    private double[] getRingValues(ImagePlus src, int radius, boolean showChannel) {
        ArrayList<ArrayList<Float>> allRingValues = new ArrayList<>();
        for (int i=0; i <= radius; i++) {
            allRingValues.add(new ArrayList<Float>());
        }
        ImageProcessor imp = src.getProcessor();
        imp.setColor(Color.white);
        for (int r = -radius; r <= radius; r++) {
            for (int c = -radius; c <= radius; c++) {
                int distance = (int) Math.round(Math.sqrt(r * r + c * c));
                if (distance <= radius) {
                    int x = (int) xCentroid + r;
                    int y = (int) yCentroid + c;
                    allRingValues.get(distance).add(imp.getPixelValue(x, y));
                    if(distance%2==0) imp.drawDot(x,y);
                }
            }
        }

        if(showChannel) src.show();
        double[] avgRingValues = new double[radius+1];
        for (int i=0; i<allRingValues.size(); i++){
            ArrayList<Float> currRing = allRingValues.get(i);
            long sum = 0;
            for (float intensity : currRing) {
                sum += intensity;
            }
            avgRingValues[i] = sum / currRing.size();
        }
        return avgRingValues;
    }

    private double[] getConvexHullValues(ImagePlus src, Roi roi, int radius) {
        Roi convexHullRoi = new PolygonRoi(roi.getConvexHull(),Roi.POLYGON);
        ImageProcessor mask = convexHullRoi.getMask();
//        new ImagePlus("mask", mask).show();

        Rectangle r = roi.getBounds();
        ArrayList<ArrayList<Float>> allConvexHullValues = new ArrayList<>();
        for (int i=0; i <= radius; i++) {
            allConvexHullValues.add(new ArrayList<Float>());
        }

        ImageProcessor imp = src.getProcessor();
        for (int y = 0; y <= r.width; y++) {
            for (int x = 0; x <= r.height; x++) {
                if (mask.getPixel(x,y)!=0) {
                    double angle = Math.atan2(y - yCentroid, x - xCentroid);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    for(int i=0; i < radius;i++) {
                        int x2 = (int) Math.round(cos * i) + x;
                        int y2 = (int) Math.round(sin * i) + y;
                        if (mask.getPixel(x2, y2) == 0) {
                            double xDiff = x - x2;
                            double yDiff = y - y2;
                            int distance = (int) Math.round(Math.sqrt(xDiff*xDiff + yDiff*yDiff));
                            allConvexHullValues.get(distance).add(imp.getPixelValue(r.x+x, r.y+y));
                            break;
                        }
                    }
                }
            }
        }

        double[] avgRingValues = new double[radius+1];
        for (int i=0; i<allConvexHullValues.size(); i++){
            ArrayList<Float> currRing = allConvexHullValues.get(i);
            if(currRing.size() >0) {
                long sum = 0;
                for (float intensity : currRing) {
                    sum += intensity;
                }
                avgRingValues[i] = sum / currRing.size();
            }
        }
        return avgRingValues;
    }

    private void addValuesToResultsTable(LinkedHashMap<String, Double> results) {
        if (table==null) table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Plot", plotTitle);
        for (String s : results.keySet()) {
            table.addValue(s, results.get(s));
        }

        table.show("Plot Results");
    }

    private double[] getMaxCoordinates(double[] values) {
        double x = 0;
        double y = 0;

        for (int i = 0; i < values.length; i++) {
            if(values[i] > y) {
                y = values[i];
                x = i;
            }
        }

        return new double[]{x, y};
    }

    private double[] getRightGradientChange(double[] values) {
        double gradient;
        int deltaX = values.length / 16; // span dependent from profile line length
        double precision = 0.1;

        for (int i = values.length - 1; i > deltaX; i--) {
            gradient = (values[i] - values[i-deltaX]) / deltaX; // deltaY / deltaX
//            System.out.println(i - span + ": " + gradient);
            if (gradient < -precision || gradient > precision) {
                if(i == values.length - 1) {
//                    IJ.showMessage("No Bounds", "The gradient on the very edge is not 0\nTry to vary the length of the profile lines or a different plot mode.");
                    break;
                } else {
                    double x = i - deltaX;
                    double y = values[i - deltaX];
                    return new double[]{x, y};
                }
            }
        }
        return new double[]{values.length - 1, values[values.length-1]};
    }

    private double getArea(double[] values, double bounds) {
        double area = 0;
        for (int i = 0; i < bounds; i++) {
            area += values[i];
        }
        return area;
    }

    private int getRadius(Roi roi, int variance) {
        Rectangle bounds = roi.getBounds();
        double diameter = bounds.getWidth() > bounds.getHeight() ? bounds.getWidth() : bounds.getHeight();
        int radius = (int) (diameter / 2);
        radius += variance * diameter / 100; // scale

        return radius;
    }
}
