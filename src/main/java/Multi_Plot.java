import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;

import java.awt.*;

/**
 * Created by mmas255 on 14/10/2016.
 */
public class Multi_Plot {

    private ImagePlus image;
    private Roi roi;
    private double xCentroid;
    private double yCentroid;

    public  Multi_Plot(ImagePlus image, Roi roi) {
        this.image = image;
        this.roi = roi;

        initCentroid();
    }

    private void initCentroid() {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        xCentroid = stats.xCentroid;
        yCentroid = stats.yCentroid;
    }

    private void runApplication() {
        RoiManager roiManager = RoiManager.getInstance();

        Rectangle bounds = roi.getBounds();

        //todo: why doesn't it add the last roi to the roi manager??
        roiManager.addRoi(new Line(bounds.x,yCentroid,bounds.x+bounds.getWidth(),yCentroid));
        roiManager.addRoi(new Line(xCentroid, bounds.y, xCentroid, bounds.y + bounds.getHeight()));
        roiManager.addRoi(new Line(bounds.x, bounds.y,bounds.x + bounds.getWidth(),bounds.y + bounds.getHeight()));
        roiManager.addRoi(new Line(bounds.x + bounds.getWidth(), bounds.y, bounds.x, bounds.y + bounds.getHeight()));

        roiManager.runCommand(image,"Show All");
    }


}
