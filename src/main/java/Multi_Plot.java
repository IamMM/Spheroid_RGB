import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.RoiEnlarger;
import ij.plugin.RoiRotator;
import ij.plugin.filter.Analyzer;
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

    private double yCentroid;
    private double xCentroid;
    private double yMax;
    private int xMax;
    private String plotTitle;
    private ResultsTable table;
    private int roiPosition;
    static final int STAR_PLOT = 0;
    static final int RING_PLOT = 1;
    static final int CONVEX_HULL = 2;

    void run(ArrayList<ImagePlus> channel, ImagePlus mask, int numberOfProfiles, boolean radiusMode, int variance, int customYMax, boolean[] options, int mode) {
        yMax = 0;
        xMax = 0;

        Roi roi = mask.getRoi();
        roiPosition = roi.getPosition() == 0? 1:roi.getPosition();

        // options
        boolean cleanTable = options[0];
        boolean showOverlay = options[1];
        boolean showChannel = options[2];
        boolean plotAll = options[3];
        boolean autoScale = options[4];
        boolean autoBounds = options[5];

        if(cleanTable) table = new ResultsTable();
        initCentroid(roi);
        int radius = getRadius(roi, variance);
        String xLabel;
        LinkedHashMap<String, double[]> intensityValues;
        String imgAndRoiTitle = " (" + mask.getTitle() + " | " + roi.getName() + ")";

        switch (mode) {
            case STAR_PLOT:
                plotTitle = "Star plot" + imgAndRoiTitle;
                ArrayList<Roi> lines = initLines(radiusMode, radius, numberOfProfiles);
                if (showChannel) showLines(channel, lines);
                showLines(mask, lines, !showOverlay);
                HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles = createAllProfiles(channel, roi, lines);
                if(!autoScale) yMax = customYMax;
                plotStarAverage(listOfAllProfiles, plotAll, radiusMode, autoBounds);
                break;
            case RING_PLOT:
                plotTitle = "Ring plot" + imgAndRoiTitle;
                xLabel = "Distance from centroid (pixels)";
                showOuterRingAndCentroid(mask, radius, !showOverlay, roi.getPosition());
                if(showChannel) showOuterRingAndCentroid(channel, radius);
                intensityValues = collectRingValues(channel, radius);
                if(!autoScale) yMax = customYMax;
                plot(intensityValues, xLabel, autoBounds);
                break;
            case CONVEX_HULL:
                plotTitle = "Convex hull plot" + imgAndRoiTitle;
                roi = roiToConvexHull(roi, variance);
                xLabel = "Distance from surface edge (pixels)";
                showHullAndCentroid(mask, roi, !showOverlay);
                if(showChannel) showHullAndCentroid(channel, roi);
                intensityValues = collectConvexHullValues(channel, roi, radius);
                if(!autoScale) yMax = customYMax;
                plot(intensityValues, xLabel, autoBounds);
                break;
        }

    }

    private Color toColor(String color) {
        switch (color) {
            case "red": return Color.red;
            case "green": return Color.green;
            case "blue": return Color.blue;
            default: return Color.black;
        }
    }

    private void initCentroid(Roi roi) {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        xCentroid = stats.xCentroid;
        yCentroid = stats.yCentroid;
        Calibration calibration = roi.getImage().getCalibration();
        if (calibration.scaled()) {
            xCentroid = calibration.getRawX(xCentroid);
            yCentroid = calibration.getRawY(yCentroid);
        }
    }

    private ArrayList<Roi> initLines(boolean radiusMode, int radius, int numberOfProfiles) {
        ArrayList<Roi> lines =  new ArrayList<>();
        double angle;
        if (radiusMode) {
            double newAngle = 0;
            angle = 360 / (double) numberOfProfiles;
            for (int i = 0; i <numberOfProfiles;i++) {
                double deltaX = Math.cos(Math.toRadians(newAngle)) * radius;
                double deltaY = Math.sin(Math.toRadians(newAngle)) * radius;
                Line line = new Line(xCentroid, yCentroid, xCentroid + deltaX, yCentroid + deltaY);
                line.setPosition(roiPosition);
                lines.add(line);
                newAngle += angle;
            }
        } else {
            angle = 180 / (double) numberOfProfiles;
            Roi horizontal = new Line(xCentroid - radius, yCentroid, xCentroid + radius, yCentroid);
            horizontal.setPosition(roiPosition);
            for (int i = 0; i <numberOfProfiles;i++) {
                horizontal = RoiRotator.rotate(horizontal, angle);
                lines.add(horizontal);
            }
        }

        return lines;
    }

    private void showLines(ImagePlus image, ArrayList<Roi> lines, boolean hide) {
        Overlay overlay = new Overlay();
        for (Roi l : lines) {
            overlay.add(l);
        }

        image.setOverlay(overlay);
        image.setHideOverlay(hide);
        image.show();
    }

    private void showLines(ArrayList<ImagePlus> channel, ArrayList<Roi> lines) {
        for (ImagePlus currChannel : channel) {
            Overlay overlay = new Overlay();
            for (Roi l : lines) {
                overlay.add(l);
            }

            currChannel.setOverlay(overlay);
            currChannel.show();
        }
    }

    private HashMap<ImagePlus , ArrayList<double[]>> createAllProfiles(ArrayList<ImagePlus> channel, Roi roi, ArrayList<Roi> lines) {
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

    private void plotStarAverage(HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles, boolean plotAll, boolean radius, boolean autoBounds) {
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
                plot.setLineWidth(2);
            }

            //average plot
            double[] avg = avgProfile(profiles);
            String title = currChannel.getTitle();
            Color avgColor = toColor(title);
            plot.setColor(avgColor);
            plot.addPoints(x, avg, PlotWindow.LINE);

            if (radius) {
                double[] max = getMaxCoordinates(avg);
                String channelName = title.equals("red")||title.equals("green")||title.equals("blue")?title:"";
                resultValues.put(channelName + " max x", max[0]);
                resultValues.put(channelName + " max y", max[1]);
                plot.setLineWidth(1);
                plot.setColor(Color.darkGray);
                plot.drawDottedLine(0, max[1], max[0], max[1], 2);

                if (autoBounds) {
                    double[] bounds = getGradientFromLeft(avg);
                    resultValues.put(channelName + " bounds x", bounds[0]);
                    resultValues.put(channelName + " bounds y", bounds[1]);
                    plot.setColor(Color.darkGray);
                    plot.drawLine(bounds[0], 0, bounds[0], yMax);
//                    double area = getArea(avg, bounds[0]);
//                    resultValues.put(channelName + " area", area);
                }
            }
        }

        if (radius) {
            resultValues.put("radius (x max)", (double) xMax);
            addValuesToResultsTable(resultValues);
        }

        plot.show();
    }

    private LinkedHashMap<String, double[]> collectRingValues (ArrayList<ImagePlus> channel, int radius) {
        // collect all intensity values to find yMax and xMax
        LinkedHashMap<String, double[]> intensityValues = new LinkedHashMap<>();
        for (ImagePlus currChannel : channel) {
            double[] y = getRingValues(currChannel, radius);
            intensityValues.put(currChannel.getTitle(), y);
            double yMaxNew = getMaxCoordinates(y)[1];
            yMax = yMaxNew > yMax ? yMaxNew : yMax;
            xMax = y.length > xMax ? y.length : xMax;
        }
        return intensityValues;
    }

    private LinkedHashMap<String, double[]> collectConvexHullValues (ArrayList<ImagePlus> channel, Roi roi, int radius) {
        // collect all intensity values to find yMax and xMax
        LinkedHashMap<String, double[]> intensityValues = new LinkedHashMap<>();
        for (ImagePlus currChannel : channel) {
            double[] y = getConvexHullValues(currChannel, roi, radius);
            intensityValues.put(currChannel.getTitle(), y);
            double yMaxNew = getMaxCoordinates(y)[1];
            yMax = yMaxNew > yMax ? yMaxNew : yMax;
            xMax = y.length > xMax ? y.length : xMax;
        }
        return intensityValues;
    }

    private void plot(LinkedHashMap<String, double[]> intensityValues, String xLabel, boolean autoBounds) {
        // init x values 0 .. xMax
        double[] x = new double[xMax];
        for (int i = 0; i < xMax; i++) {
            x[i] = (double) i;
        }

        // plot
        PlotWindow.noGridLines = false; // draw grid lines
        Plot plot = new Plot(plotTitle,xLabel,"Intensity (gray value)");
        plot.setLimits(0, xMax, 0, yMax + 1);
        plot.setLineWidth(1);

        LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();
        for (String title : intensityValues.keySet()) {
            double[] y = intensityValues.get(title);
            plot.setColor(toColor(title));
            plot.addPoints(x, y,PlotWindow.LINE);

            double[] max = getMaxCoordinates(y);
            String channelName = title.equals("red")||title.equals("green")||title.equals("blue")?title:"";
            resultValues.put(channelName + " max x", max[0]);
            resultValues.put(channelName + " max y", max[1]);
            plot.setColor(Color.darkGray);
            plot.drawDottedLine(0, max[1], max[0], max[1], 2);

            if(autoBounds) {
                double[] bounds = getBounds(y);
                resultValues.put(title + " bounds x", bounds[0]);
                resultValues.put(title + " bounds y", bounds[1]);
                plot.setColor(Color.darkGray);
                plot.drawLine(bounds[0], 0, bounds[0], 255);
//                double area = getArea(y, bounds[0]);
//                resultValues.put(title + " area", area);
            }

        }
        resultValues.put("radius (x max)", (double) xMax);
        addValuesToResultsTable(resultValues);
        plot.show();
    }

    private void showOuterRingAndCentroid(ImagePlus src, int radius, boolean hide, int roiPosition) {
        Overlay overlay= new Overlay();
        PointRoi pointRoi = new PointRoi(xCentroid,yCentroid);
        pointRoi.setPosition(roiPosition);
        overlay.add(pointRoi);
        OvalRoi outerRing =new OvalRoi(xCentroid - radius,yCentroid-radius,radius<<1,radius<<1);
        outerRing.setPosition(roiPosition);
        overlay.add(outerRing);
        src.setOverlay(overlay);
        src.setHideOverlay(hide);
    }

    private void showOuterRingAndCentroid(ArrayList<ImagePlus> channels, int radius) {
        for (ImagePlus channel : channels) {
            Overlay overlay= new Overlay();
            PointRoi pointRoi = new PointRoi(xCentroid,yCentroid);
            pointRoi.setPosition(roiPosition);
            overlay.add(pointRoi);
            OvalRoi outerRing =new OvalRoi(xCentroid - radius,yCentroid-radius,radius<<1,radius<<1);
            outerRing.setPosition(roiPosition);
            overlay.add(outerRing);
            channel.setOverlay(overlay);
            channel.show();
        }
    }

    private void showHullAndCentroid(ImagePlus src, Roi roi, boolean hide) {
        Overlay overlay= new Overlay();
        PointRoi pointRoi = new PointRoi(xCentroid,yCentroid);
        pointRoi.setPosition(roiPosition);
        overlay.add(pointRoi);
        roi.setPosition(roiPosition);
        overlay.add(roi);
        src.setOverlay(overlay);
        src.setHideOverlay(hide);
    }

    private void showHullAndCentroid(ArrayList<ImagePlus> channels, Roi roi) {
        for (ImagePlus channel : channels) {
            Overlay overlay= new Overlay();
            PointRoi pointRoi = new PointRoi(xCentroid,yCentroid);
            pointRoi.setPosition(roiPosition);
            overlay.add(pointRoi);
            roi.setPosition(roiPosition);
            overlay.add(roi);
            channel.setOverlay(overlay);
            channel.show();
        }
    }

    private double[] getRingValues(ImagePlus src, int radius) {
        ArrayList<ArrayList<Float>> allRingValues = new ArrayList<>();
        for (int i=0; i <= radius; i++) {
            allRingValues.add(new ArrayList<Float>());
        }
        ImageProcessor imp = src.getStack().getProcessor(roiPosition);
        for (int r = -radius; r <= radius; r++) {
            for (int c = -radius; c <= radius; c++) {
                int distance = (int) Math.round(Math.sqrt(r*r + c*c));
                if (distance <= radius) {
                    int x = (int) xCentroid + r;
                    int y = (int) yCentroid + c;
                    allRingValues.get(distance).add(imp.getPixelValue(x, y));
                }
            }
        }

        double[] avgRingValues = new double[radius+1];
        for (int i=0; i<allRingValues.size(); i++){
            ArrayList<Float> currRing = allRingValues.get(i);
            long sum = 0;
            for (float intensity : currRing) {
                sum += intensity;
            }
            avgRingValues[i] = sum / (double) currRing.size();
        }
        return avgRingValues;
    }

    private double[] getConvexHullValues(ImagePlus src, Roi roi, int radius) {
        ImageProcessor mask = roi.getMask();

        Rectangle r = roi.getBounds();
        ArrayList<ArrayList<Float>> allConvexHullValues = new ArrayList<>();
        for (int i=0; i <= radius; i++) {
            allConvexHullValues.add(new ArrayList<Float>());
        }

        ImageProcessor imp = src.getStack().getProcessor(roiPosition);
        for (int y = 0; y <= r.width; y++) {
            for (int x = 0; x <= r.height; x++) {
                if (mask.getPixel(x,y)!=0) {
                    double angle = Math.atan2(y - yCentroid, x - xCentroid);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);

                    // binary search
                    int low = 0, mid, high = radius;
                    int x2, y2;
                    while (low < high) {
                        mid = (low + high) / 2;
                        x2 = (int) Math.round(cos * mid) + x;
                        y2 = (int) Math.round(sin * mid) + y;
                        if (mask.getPixel(x2, y2) == 0) {
                            high = mid - 1;
                        } else {
                            low = mid + 1;
                        }
                    }

                    // go back from here step by step until in selection again
                    for(int length=high; length >=0;length--) {
                        x2 = (int) (cos * length) + x;
                        y2 = (int) (sin * length) + y;
                        if (mask.getPixel(x2, y2) != 0) {
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

        double[] avgValues = new double[allConvexHullValues.size()];
        for (int i=0; i<allConvexHullValues.size(); i++){
            ArrayList<Float> currRing = allConvexHullValues.get(i);
            if(currRing.size() >0) {
                long sum = 0;
                for (float intensity : currRing) {
                    sum += intensity;
                }
                avgValues[i] = sum / (double) currRing.size();
            }
        }
        return avgValues;
    }

    private void addValuesToResultsTable(LinkedHashMap<String, Double> results) {
        if (table==null) table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Plot", plotTitle);
        for (String s : results.keySet()) {
            table.addValue(s, results.get(s));
        }

        Analyzer.setResultsTable(table);
        table.show("Results");
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

    private double[] getBounds(double[] values) {
        values = smooth(values);

        int length = values.length;
        double left = (values[0] + values[1] + values[2]) / 3;
        double right = (values[length-3] + values[length-2] + values[length-1]) / 3;
        boolean fromLeft = left < right;

        return fromLeft? getGradientFromLeft(values):getGradientFromRight(values);
    }

    private double[] smooth(double[] values) {
        double[] smooth = new double[values.length];
        for (int i=1; i < values.length-1; i++){
            smooth[i] = (values[i-1] + values[i] + values[i+1]) / 3;
        }
        return smooth;
    }

    private double[] getGradientFromLeft(double[] values) {
        double gradient;
        int deltaX = values.length / 16; // span dependent from profile line length
        double precision = 0.1;

        for (int i = 0; i < values.length - deltaX; i++) {
            gradient = (values[i] - values[i+deltaX]) / deltaX; // deltaY / deltaX
            if (gradient < -precision || gradient > precision) {
                double x = i + deltaX;
                double y = values[i + deltaX];
                return new double[]{x, y};
            }
        }
        return new double[]{0,0};
    }

    private double[] getGradientFromRight(double[] values) {
        double gradient;
        int deltaX = values.length / 16; // span dependent from profile line length
        double precision = 0.1;

        for (int i = values.length - 1; i > deltaX; i--) {
            gradient = (values[i] - values[i-deltaX]) / deltaX; // deltaY / deltaX
            if (gradient < -precision || gradient > precision) {
                double x = i - deltaX;
                double y = values[i - deltaX];
                return new double[]{x, y};
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
    private Roi roiToConvexHull(Roi roi, int variance) {
        Rectangle bounds = roi.getBounds();
        double diameter = bounds.getWidth() > bounds.getHeight() ? bounds.getWidth() : bounds.getHeight();
        roi = new PolygonRoi(roi.getConvexHull(),Roi.POLYGON);
        return  RoiEnlarger.enlarge(roi, variance * diameter / 100);
    }
}
