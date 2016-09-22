import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Spheroid_RGB
 * A plugin for analysing each channel of RGB Image
 * Created on September 20, 2016
 *
 * @author Maximilian Maske
 */
public class Spheroid_RGB implements PlugIn {
    private final String version = " v1.0 ";

    // imageJ components
    private ImagePlus image;

    // image property members
    private int width;
    private int height;

    //ITCN values
    private int cellWidth;
    private double threshold;
    private boolean darkPeaks;

    // what channels to take
    private boolean takeR;
    private boolean takeG;
    private boolean takeB;

    // split channels
    private ImagePlus rChannel;
    private ImagePlus gChannel;
    private ImagePlus bChannel;

    //Results table instance
    private ResultsTable resultsTable;

    /**
     * Main method for debugging.
     * For debugging, it is convenient to have a method that starts ImageJ, loads an
     * image and calls the plugin, e.g. after setting breakpoints.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
        Class<?> clazz = Spheroid_RGB.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // open the Spheroid sample
        ImagePlus image = IJ.openImage("P:/image analysis/cell count/EdU_slide2.2.tif");
        image.show();

        // run the plugin
//        IJ.runPlugIn(clazz.getName(), "");
    }

    /**
     * @see ij.plugin.PlugIn#run(String)
     */
    @Override
    public void run(String arg) {
//        if (IJ.versionLessThan("1.36b")) return;
        image = WindowManager.getCurrentImage();
        width = image.getWidth();
        height = image.getHeight();

        if (showDialog()) {
            process();
            runApplication();
        }
    }

    private boolean showDialog() {
        if(RoiManager.getInstance() == null) {
            IJ.showMessage("Please select ROI and add to ROI Manager");
            return false;
        }

        GenericDialog gd = new GenericDialog("Spheroid RGB");

        gd.setAlwaysOnTop(true);
        gd.addMessage("Parameter for Automated Cell Count");
        gd.addNumericField("cell width", 15.00, 0);
        gd.addSlider("threshold", 0.0, 1.0, 0.2);
        gd.addCheckbox("Count dark peaks", false);

        String[] labels = new String[]{"R", "G", "B"};
        boolean[] defaultValues = new boolean[]{true, false, true};

        gd.addMessage("Choose Color Channels");
        gd.addCheckboxGroup(3, 1, labels, defaultValues);

        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        // get entered values
        cellWidth = (int) gd.getNextNumber();
        threshold = gd.getNextNumber();
        darkPeaks = gd.getNextBoolean();

        takeR = gd.getNextBoolean();
        takeG = gd.getNextBoolean();
        takeB = gd.getNextBoolean();

        return true;
    }

    private void runApplication() {
        //create Results table
        resultsTable = Analyzer.getResultsTable();
        if (resultsTable == null) {
            resultsTable = new ResultsTable();
            Analyzer.setResultsTable(resultsTable);
        }

        HashMap<ImagePlus, ImageProcessor> channel = new HashMap<ImagePlus, ImageProcessor>();
        if(rChannel != null) {
            ImageProcessor redResults = (rChannel.getProcessor().duplicate()).convertToRGB();
            channel.put(rChannel, redResults);
        }
        if(gChannel != null) {
            ImageProcessor greenResults = (gChannel.getProcessor().duplicate()).convertToRGB();
            channel.put(gChannel, greenResults);
        }
        if(bChannel != null) {
            ImageProcessor blueResults = (bChannel.getProcessor().duplicate()).convertToRGB();
            channel.put(bChannel, blueResults);
        }

        // count cells from selected channels
        // for each Roi from Roi Manager
        // ITCN_Runner does the job of counting the cells
        for (Roi currRoi : RoiManager.getInstance().getRoisAsArray()) {
            resultsTable.incrementCounter();
            resultsTable.addValue("ROI", currRoi.getName());
            for (ImagePlus currChannel : channel.keySet()) {
                currChannel.setRoi(currRoi);
                runITCN(currChannel, currChannel.getTitle(), channel.get(currChannel));
            }

            resultsTable.addResults();
            resultsTable.updateResults();
        }

        ArrayList<ImagePlus> resultImages = new ArrayList<ImagePlus>();
        for (ImagePlus currChannel : channel.keySet()) {
            resultImages.add(new ImagePlus("Results " + currChannel.getTitle(), channel.get(currChannel)));
        }

        for (ImagePlus currImage : resultImages) {
            currImage.show();
        }

//        String strFrame = "Spheroid RGB " + version + " (" + image.getTitle() + ")";
//        resultsTable.show(strFrame); //results should only shown in the Results window
    }

    private void runITCN(ImagePlus imp, String color, ImageProcessor ipResults) {
        //min distance = cell width / 2 as recommended AND maskImp = null (ROI)
        ITCN_Runner itcn;
        itcn = new ITCN_Runner(imp, cellWidth, (double) cellWidth / 2., threshold, darkPeaks, null);
        itcn.run();

        int numberOfCells = itcn.getNumberOfCells();
        ArrayList<Point> peaks = itcn.getPeaks();
        resultsTable.addValue("number of " + color + " cells", numberOfCells);

        //draw peaks
        ipResults.setColor(java.awt.Color.WHITE);
        ipResults.setLineWidth(1);

        Point pt;
        for (int i = 0; i < numberOfCells; i++) {
            pt = peaks.get(i);

            ipResults.drawDot(pt.x, pt.y);

            //IJ.write("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
        }

        ipResults.setColor(java.awt.Color.YELLOW);
        imp.getRoi().drawPixels(ipResults);
    }

    // check if Image is RGB
    private void process() {
        int type = image.getType();
        if (type == ImagePlus.GRAY8) {
            rChannel = image;
        } else if (type == ImagePlus.GRAY16)
            IJ.showMessage("16-bit gray scale image not supported");
        else if (type == ImagePlus.GRAY32)
            IJ.showMessage("32-bit gray scale image not supported");
        else if (type == ImagePlus.COLOR_RGB) {
            splitChannels(image);
        } else {
            IJ.showMessage("not supported");
        }
    }

    //split channels
    private void splitChannels(ImagePlus imp) {
        ImagePlus[] rgb = ChannelSplitter.split(imp);

        if (takeR) {
            rChannel = rgb[0];
            rChannel.setLut(LUT.createLutFromColor(Color.RED));
//            rChannel.show();
        }
        if (takeG) {
            gChannel = rgb[1];
            gChannel.setLut(LUT.createLutFromColor(Color.GREEN));
//            gChannel.show();
        }
        if (takeB) {
            bChannel = rgb[2];
            bChannel.setLut(LUT.createLutFromColor(Color.BLUE));
//            bChannel.show();
        }
    }

    public void showAbout() {
        IJ.showMessage("Spheroid RGB",
                "a plugin for analysing a stained image of a spheroid"
        );
    }
}
