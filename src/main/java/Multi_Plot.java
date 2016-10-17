import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
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
    private ArrayList<Roi> lines =  new ArrayList<Roi>();
    private int numberOfProfiles;
    private double ANGLE;

    Multi_Plot(ImagePlus image, int numberOfProfiles) {
        this.image = image;
        this.roi = image.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        ANGLE = 180 / (double) numberOfProfiles;

        initCentroid();
        initLines();
    }

    Multi_Plot(ImagePlus image, ImagePlus mask, int numberOfProfiles) {
        this.image = image;
        image.setRoi(mask.getRoi());
        this.roi = image.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        ANGLE = 180 / (double) numberOfProfiles;

        initCentroid();
        initLines();
    }

    private void initCentroid() {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        yCentroid = stats.yCentroid;
    }

    private void initLines() {
        Rectangle bounds = roi.getBounds();
        Roi horizontal = new Line(bounds.x,yCentroid,bounds.x+bounds.getWidth(),yCentroid);;
        for (int i = 0; i <numberOfProfiles;i++) {
            horizontal = RoiRotator.rotate(horizontal, ANGLE);
            lines.add(horizontal);
        }

        Overlay overlay = new Overlay();
        for (Roi l : lines) {
            overlay.add(l);
        }

        image.setOverlay(overlay);
        image.setRoi(roi);
        image.show();
    }

    void plotAll() {
        for (Roi l : lines) {
            image.setRoi(l);
            ProfilePlot profilePlot = new ProfilePlot(image);
            profilePlot.createWindow();
        }
    }
    
    void plotAverage() {
        for (Roi l : lines) {
            //todo continue here
        }
    }
}
