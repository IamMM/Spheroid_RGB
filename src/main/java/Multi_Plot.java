import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.RoiRotator;
import ij.process.ImageStatistics;

import javax.xml.transform.Result;
import java.awt.*;
import java.util.ArrayList;

/**
 * Created on 14/10/2016.
 *
 * @author Maximilian Maske
 */
class Multi_Plot {

    private ImagePlus image;
    private Roi roi;
    private double yCentroid;
    private double xCentroid;
    private ArrayList<Roi> lines;
    private int numberOfProfiles;
    private double ANGLE;
    private ArrayList<ProfilePlot> profilePlots;
    private ArrayList<double[]> profiles;
    private double yMax = 0;
    private String plotTitle;
    private Color plotColor;
    private ResultsTable table;

    void run(ImagePlus image, int numberOfProfiles, boolean diameter, int profileLength) {
        this.image = image;
        this.roi = image.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        if(diameter) ANGLE = 180 / (double) numberOfProfiles;
        else ANGLE = 360 / (double) numberOfProfiles;
        plotTitle = "Plot " + image.getTitle();

        plotColor = Color.black;

        initCentroid();
        initLines(diameter, profileLength);
        showLines();
    }

    void run(ImagePlus image, ImagePlus mask, int numberOfProfiles, boolean diameter, int profileLength) {
        this.image = image;
        image.setRoi(mask.getRoi());
        this.roi = image.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        if(diameter) ANGLE = 180 / (double) numberOfProfiles;
        else ANGLE = 360 / (double) numberOfProfiles;
        plotTitle = "Plot " + mask.getTitle() + " (" + image.getTitle() + ")";

        setPlotColor(image.getTitle());

        initCentroid();
        initLines(diameter, profileLength);
        showLines();
    }

    private void setPlotColor(String title) {
        if (title.equals("red")) plotColor = Color.red;
        else if (title.equals("green")) plotColor = Color.green;
        else if (title.equals("blue")) plotColor = Color.blue;
    }

    private void initCentroid() {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        yCentroid = stats.yCentroid;
        xCentroid = stats.xCentroid;
    }

    private void initLines(boolean diameter, int profileLength) {
        lines =  new ArrayList<Roi>();
        Rectangle bounds = roi.getBounds();
        int x1 = (int) (bounds.x - profileLength * bounds.getWidth() / 100);
        int x2 = (int) (bounds.x + bounds.getWidth() + profileLength * bounds.getWidth() / 100);
        if(diameter) {
            Roi horizontal = new Line(x1, yCentroid, x2, yCentroid);
            for (int i = 0; i <numberOfProfiles;i++) {
                horizontal = RoiRotator.rotate(horizontal, ANGLE);
                lines.add(horizontal);
            }
        }
        else {
            double newAngle = 0;
            int radius = (int) (bounds.getWidth() / 2 + profileLength * bounds.getWidth() / 100);
            for (int i = 0; i <numberOfProfiles;i++) {
                double deltaX = Math.cos(Math.toRadians(newAngle)) * radius;
                double deltaY = Math.sin(Math.toRadians(newAngle)) * radius;
                lines.add(new Line(xCentroid, yCentroid, xCentroid + deltaX, yCentroid + deltaY));
                newAngle += ANGLE;
            }
        }
    }

    private void showLines() {
        Overlay overlay = new Overlay();
        for (Roi l : lines) {
            overlay.add(l);
        }

        image.setOverlay(overlay);
        image.show();
    }

    private void createAllPlots() {
        profilePlots = new ArrayList<ProfilePlot>();
        profiles = new ArrayList<double[]>();
        for (Roi l : lines) {
            image.setRoi(l);
            ProfilePlot profilePlot = new ProfilePlot(image);
            profiles.add(profilePlot.getProfile());
            if(profilePlot.getMax() > yMax) {
                yMax = profilePlot.getMax();
            }
            profilePlots.add(profilePlot);
        }
        IJ.run("Select None");
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

    void plotAll() {
        createAllPlots();
        for (ProfilePlot p : profilePlots) {
            p.createWindow();
        }
        image.setRoi(roi);
    }
    
    void plotAverage() {
       createAllPlots();

        double[] x = new double[profiles.get(0).length];
        for (int i = 0; i < x.length; i++) {
            x[i] = (double) i;
        }

        PlotWindow.noGridLines = false; // draw grid lines
        Plot plot = new Plot(plotTitle,"Distance","Intensity");
        plot.setLimits(0, x.length, 0, yMax);
        plot.setLineWidth(1);
        plot.setColor(Color.gray);

        for (double[] y : profiles) {
            plot.addPoints(x,y,PlotWindow.LINE);
        }

        //average plot
        double[] avg = avgProfile(profiles);
        plot.setColor(plotColor);
        plot.setLineWidth(2);
        plot.addPoints(x, avg, PlotWindow.LINE);

        double[] max = getMaxCoordinates(avg);
//        double[] min = getMinGradient(avg, (int) max[0]);
        double[] bounds = getRightGradientChange(avg);
        plot.setLineWidth(1);
        plot.setColor(Color.BLACK);
        plot.addPoints(new double[]{max[0]}, new double[]{max[1]}, PlotWindow.X);
        plot.addPoints(new double[]{bounds[0]}, new double[]{bounds[1]}, PlotWindow.X);
        plot.drawLine(bounds[0],0,bounds[0],bounds[1]);
        plot.drawLine(0,max[1],max[0],max[1]);

        plot.show();

        addValuesToResultsTable(max,bounds);

        image.setRoi(roi);
    }

    private void addValuesToResultsTable(double[]max, double[]bounds) {
        if (table==null){
            table = new ResultsTable();
        }
        table.incrementCounter();
        table.addValue("Plot", plotTitle);
        table.addValue("max x", max[0]);
        table.addValue("max y", max[1]);
        table.addValue("bounds x", bounds[0]);
        table.addValue("bounds y", bounds[1]);
        table.show("Plot Values");
    }

    private double[] getMaxCoordinates(double[] values) {
        double x = 0;
        double y = 0; //value

        for (int i = 0; i < values.length; i++) {
            if(values[i] > y) {
                y = values[i];
                x = i;
            }
        }

        return new double[]{x, y};
    }

    private double[] getMinGradient(double[] values, int max) {
        double gradient;
        double x;
        double y;
        int span = values.length / 16;
        double precision = 0.01;

        for (int i = max + span; i < values.length; i++) {
            gradient = (values[i] - values[i-span]) / span;
            System.out.println(i - span + ": " + gradient);
            if (gradient < precision && gradient > -precision) {
                x = i - span;
                y = values[i-span];
                return new double[]{x, y};
            } else if (i == values.length - 1) {
                return new double[]{i, values[i]};
            }
        }

        return null;
    }

    private double[] getRightGradientChange(double[] values) {
        double gradient;
        double x;
        double y;
        int span = values.length / 16;
        double precision = 0.1;

        for (int i = values.length - 1; i > span; i--) {
            gradient = (values[i] - values[i-span]) / span;
            System.out.println(i - span + ": " + gradient);
            if (gradient < -precision) {
                if(i == values.length - 1) {
                    IJ.showMessage("No Bounds", "Please make the profile lines longer.");
                    return new double[]{values.length - 1, values[values.length-1]};
                } else {
                    x = i - span;
                    y = values[i - span];
                    return new double[]{x, y};
                }
            }
        }

        return null;
    }
}
