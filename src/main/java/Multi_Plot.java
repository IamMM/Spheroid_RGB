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
    private double xCentroid;
    private double yCentroid;
    private ArrayList<Roi> lines =  new ArrayList<Roi>();
    private int numberOfProfiles;
    private double ANGEL;

    public Multi_Plot(ImagePlus image, int numberOfProfiles) {
        this.image = image;
        this.roi = image.getRoi();
        this.numberOfProfiles = numberOfProfiles;
        ANGEL = 180 / (double) numberOfProfiles;

        initCentroid();
        initLines();
        plot();
    }

    private void initCentroid() {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        xCentroid = stats.xCentroid;
        yCentroid = stats.yCentroid;
    }

    private void initLines() {
        Rectangle bounds = roi.getBounds();
//        lines.add(new Line(bounds.x,yCentroid,bounds.x+bounds.getWidth(),yCentroid));
//        lines.add(new Line(xCentroid, bounds.y, xCentroid, bounds.y + bounds.getHeight()));
//        lines.add(new Line(bounds.x, bounds.y,bounds.x + bounds.getWidth(),bounds.y + bounds.getHeight()));
//        lines.add(new Line(bounds.x + bounds.getWidth(), bounds.y, bounds.x, bounds.y + bounds.getHeight()));

        Roi horizontal = new Line(bounds.x,yCentroid,bounds.x+bounds.getWidth(),yCentroid);;
        for (int i = 0; i <numberOfProfiles;i++) {
            horizontal = RoiRotator.rotate(horizontal, ANGEL);
            lines.add(horizontal);
        }

        Overlay overlay = new Overlay();
        for (Roi l : lines) {
            overlay.add(l);
        }

        image.setOverlay(overlay);
        IJ.run("Select None");
    }

    private void plot() {
        for (Roi l : lines) {
            image.setRoi(l);
            ProfilePlot profilePlot = new ProfilePlot(image);
            profilePlot.createWindow();
        }
    }
}
