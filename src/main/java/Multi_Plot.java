import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Measurements;
import ij.process.ImageStatistics;

import java.awt.*;
import java.util.ArrayList;

/**
 * Created by mmas255 on 14/10/2016.
 */
public class Multi_Plot {

    private ImagePlus image;
    private Roi roi;
    private double xCentroid;
    private double yCentroid;
    ArrayList<Line> lines =  new ArrayList<Line>();

    public  Multi_Plot(ImagePlus image) {
        this.image = image;
        this.roi = image.getRoi();

        initCentroid();
        initLines();
        plot();
    }

    private void initCentroid() {
        ImageStatistics stats = roi.getImage().getStatistics(Measurements.CENTROID);
        xCentroid = stats.xCentroid;
        yCentroid = stats.yCentroid;
    }

    public void initLines() {
        Rectangle bounds = roi.getBounds();
        lines.add(new Line(bounds.x,yCentroid,bounds.x+bounds.getWidth(),yCentroid));
        lines.add(new Line(xCentroid, bounds.y, xCentroid, bounds.y + bounds.getHeight()));
        lines.add(new Line(bounds.x, bounds.y,bounds.x + bounds.getWidth(),bounds.y + bounds.getHeight()));
        lines.add(new Line(bounds.x + bounds.getWidth(), bounds.y, bounds.x, bounds.y + bounds.getHeight()));

        Overlay overlay = new Overlay();
        for (Line l : lines) {
            overlay.add(l);
        }

        image.setOverlay(overlay);
    }

    public void plot() {
        for (Line l : lines) {
            image.setRoi(l);
            ProfilePlot profilePlot = new ProfilePlot(image);
            profilePlot.createWindow();
        }
    }
}
