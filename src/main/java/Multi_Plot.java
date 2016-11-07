import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.RoiRotator;
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

    void run(ArrayList<ImagePlus> channel, ImagePlus mask, int numberOfProfiles, boolean radius, int profileLength, String major, int customYMax, boolean[] options) {
        yMax = 0;
        xMax = 0;

        this.mask = mask;
        this.roi = mask.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        double angle;
        if(radius) angle = 360 / (double) numberOfProfiles;
        else angle = 180 / (double) numberOfProfiles;

        plotTitle = "Plot " + mask.getTitle();

        // options
        boolean showLines = options[0];
        boolean showChannel = options[1];
        boolean plotAll = options[2];
        boolean autoScale = options[3];

        initCentroid();
        initLines(radius, profileLength, angle);
        if (showLines) showLines(mask);
        if (showChannel) showLines(channel);
        HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles = createAllPlots(channel);
        if(!autoScale) yMax = customYMax;
        plotAverage(listOfAllProfiles, plotAll, major.toLowerCase());
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

    private void initLines(boolean radius, int profileLength, double angle) {
        lines =  new ArrayList<>();
        Rectangle bounds = roi.getBounds();
        int x1 = (int) (bounds.x - profileLength * bounds.getWidth() / 100);
        int x2 = (int) (bounds.x + bounds.getWidth() + profileLength * bounds.getWidth() / 100);
        if(!radius) {
            Roi horizontal = new Line(x1, yCentroid, x2, yCentroid);
            for (int i = 0; i <numberOfProfiles;i++) {
                horizontal = RoiRotator.rotate(horizontal, angle);
                lines.add(horizontal);
            }
        }
        else {
            double newAngle = 0;
            int r = (int) (bounds.getWidth() / 2 + profileLength * bounds.getWidth() / 100);
            for (int i = 0; i <numberOfProfiles;i++) {
                double deltaX = Math.cos(Math.toRadians(newAngle)) * r;
                double deltaY = Math.sin(Math.toRadians(newAngle)) * r;
                lines.add(new Line(xCentroid, yCentroid, xCentroid + deltaX, yCentroid + deltaY));
                newAngle += angle;
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

    private void plotAverage(HashMap<ImagePlus, ArrayList<double[]>> listOfAllProfiles, boolean plotAll, String major) {
        // init x values 0 .. xMax
        double[] x = new double[xMax];
        for (int i = 0; i < xMax; i++) {
            x[i] = (double) i;
        }

        PlotWindow.noGridLines = false; // draw grid lines
        Plot plot = new Plot(plotTitle,"Distance","Intensity");
        plot.setLimits(0, xMax, 0, yMax);


        // avg all profiles of all channels
        LinkedHashMap<String, Double> resultValues = new LinkedHashMap<>();
        LinkedHashMap<String, double[]> avgList = new LinkedHashMap<>();

        double[] majorBounds = new double[]{xMax, yMax};
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
            avgList.put(currChannel.getTitle(), avg);

            double[] max = getMaxCoordinates(avg);
            resultValues.put(currChannel.getTitle() + " max x", max[0]);
            resultValues.put(currChannel.getTitle() + " max y", max[1]);

            double[] bounds = getRightGradientChange(avg);
            resultValues.put(currChannel.getTitle() + " bounds x", bounds[0]);
            resultValues.put(currChannel.getTitle() + " bounds y", bounds[1]);
            plot.setColor(Color.darkGray);
            plot.setLineWidth(1);
            plot.drawLine(bounds[0],0,bounds[0],yMax);
            if(currChannel.getTitle().equalsIgnoreCase(major)) {
                majorBounds = bounds;
            }

            plot.setLineWidth(1);
            plot.setColor(Color.darkGray);
            plot.drawDottedLine(0, max[1] ,max[0], max[1], 2);
        }

        for (String color : avgList.keySet()) {
            double area = getArea(avgList.get(color), majorBounds[0]);
            resultValues.put(color + " area (" + major + " bounds)", area);
        }

        addValuesToResultsTable(resultValues);
        plot.show();
        mask.setRoi(roi);
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

//    private double[] getMinGradientLeftFromMax(double[] values, int max) {
//        double gradient;
//        int span = values.length / 16;
//        double precision = 0.1;
//
//        for (int i = max + span; i < values.length; i++) {
//            gradient = (values[i] - values[i-span]) / span;
//            System.out.println(i - span + ": " + gradient);
//            if (gradient < precision && gradient > -precision) {
//                double x = i - span;
//                double y = values[i-span];
//                return new double[]{x, y};
//            } else if (i == values.length - 1) {
//                return new double[]{i, values[i]};
//            }
//        }
//
//        return null;
//    }

    private double[] getRightGradientChange(double[] values) {
        double gradient;
        int deltaX = values.length / 16; // span dependent from profile line length
        double precision = 0.1;

        for (int i = values.length - 1; i > deltaX; i--) {
            gradient = (values[i] - values[i-deltaX]) / deltaX; // deltaY / deltaX
//            System.out.println(i - span + ": " + gradient);
            if (gradient < -precision || gradient > precision) {
                if(i == values.length - 1) {
                    IJ.showMessage("No Bounds", "The gradient on the very edge is not 0\nTry to variate the length of the profile lines.");
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
}
