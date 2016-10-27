import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Measurements;
import ij.plugin.RoiRotator;
import ij.process.ImageStatistics;

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
    private ArrayList<Roi> lines =  new ArrayList<Roi>();
    private int numberOfProfiles;
    private double ANGLE;
    private ArrayList<ProfilePlot> profilePlots;
    private ArrayList<double[]> profiles = new ArrayList<double[]>();
    private double yMax = 0;
    private String plotTitle;
    private Color plotColor;

    Multi_Plot(ImagePlus image, int numberOfProfiles, boolean diameter, int profileLength) {
        this.image = image;
        this.roi = image.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        ANGLE = 180 / (double) numberOfProfiles;
        plotTitle = "Plot " + image.getTitle();

        plotColor = Color.black;

        initCentroid();
        initLines(diameter, profileLength);
        showLines();
    }

    Multi_Plot(ImagePlus image, ImagePlus mask, int numberOfProfiles, boolean diameter, int profileLength) {
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
        Rectangle bounds = roi.getBounds();
        Roi horizontal;
        int x1 = (int) (bounds.x - profileLength * bounds.getWidth() / 100);
        int x2 = (int) (bounds.x + bounds.getWidth() + profileLength * bounds.getWidth() / 100);
        if(diameter) horizontal = new Line(x1, yCentroid, x2, yCentroid);
        else horizontal = new Line(x1,yCentroid,xCentroid,yCentroid);
        for (int i = 0; i <numberOfProfiles;i++) {
            horizontal = RoiRotator.rotate(horizontal, ANGLE);
            lines.add(horizontal);
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

        plot.setColor(plotColor);
        plot.setLineWidth(2);
        plot.addPoints(x, avgProfile(profiles), PlotWindow.LINE);

        plot.show();

        image.setRoi(roi);
    }
}
