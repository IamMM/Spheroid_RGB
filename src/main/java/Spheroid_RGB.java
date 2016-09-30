import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
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
    //constants
    private static final String TITLE = "Spheroid RGB";
    private static final String VERSION = " v0.1.0 ";
    private static Color PEAKS_COLOR = Color.WHITE;
    private static final Color ROI_COLOR = Color.YELLOW;
    private static int widthDefault = 10;
    private static double min_distDefault = 5.0;
    private static double thresDefault = 0.0;
    private static double thresPrecision = 10;

    // application window
    private PlugInFrame frame;

    //gui components
    private java.awt.Panel buttonPanel;
    private java.awt.Button exitButton;
    private java.awt.Label ccImageLabel;
    private java.awt.Checkbox darkPeaksCheckbox;
    private java.awt.Label filenameLabel;
    private java.awt.Panel imagePanel;
    private java.awt.Choice maskChoice;
    private java.awt.Label maskLabel;
    private java.awt.Panel maskPanel;
    private java.awt.Label minDistLabel;
    private java.awt.TextField minDistTextField;
    private java.awt.Label minDistUnitsLabel;
    private java.awt.Button okButton;
    private java.awt.Button openMaskButton;
    private java.awt.Label recomendLabel;
    private java.awt.Panel varsPanel;
    private java.awt.Label widthLabel;
    private java.awt.TextField widthTextField;
    private java.awt.Label widthUnitsLabel;
    private java.awt.Button widthButton;
    private java.awt.Button minDistButton;
    private java.awt.Label thresLabel;
    private java.awt.TextField thresTextField;
    private java.awt.Scrollbar thresScroll;
    private java.awt.Panel midPanel;
    private static final String strNONE = "Use selected ROI";
    private ImagePlus currImp;
    private ArrayList winIDList;

    // imageJ components
    private ImagePlus image;
    private int width;
    private int heigth;

    //ITCN values
    private int cellWidth;
    private double threshold;
    private boolean darkPeaks;

    // what channels to take
    private boolean takeR;
    private boolean takeG;
    private boolean takeB;
    private int total;

    // split channels
    private ImagePlus rChannel;
    private ImagePlus gChannel;
    private ImagePlus bChannel;

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
        ImagePlus image = IJ.openImage("C:/workspace/Spheroid_RGB/EdU_slide2.2.tif");
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
        if(WindowManager.getCurrentImage() != null) {
            image = WindowManager.getCurrentImage();
            width = image.getWidth();
            heigth = image.getHeight();
        }else {
            IJ.showMessage("No images open");
            return;
        }

        if (showDialog()) {
            process();
            runApplication();
        }
    }

    private boolean showDialog() {
        if(RoiManager.getInstance() == null) {
            IJ.showMessage("Please select ROI and add to ROI Manager");
            new RoiManager();
        }

        NonBlockingGenericDialog gd = new NonBlockingGenericDialog(TITLE + " " + VERSION);

        gd.addMessage("Parameter for Automated Cell Count");
        gd.addNumericField("cell width", 15.00, 0);
        gd.addSlider("threshold", 0.0, 1.0, 0.2);
        gd.addCheckbox("Count dark peaks", false);

        String[] labels = new String[]{"R", "G", "B"};
        boolean[] defaultValues = new boolean[]{true, false, true};

        gd.addMessage("Choose Color Channels");
        gd.addCheckboxGroup(3, 1, labels, defaultValues);

        gd.addChoice("Channel representing total number of cells", labels, labels[2]);

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

        total = gd.getNextChoiceIndex();

        return true;
    }

    private void runApplication() {
        //create Results table
        ResultsTable resultsTable = Analyzer.getResultsTable();
        if (resultsTable == null) {
            resultsTable = new ResultsTable();
            Analyzer.setResultsTable(resultsTable);
        }

        HashMap<ImagePlus, ImageProcessor> channel = initChannelMap();

        //count cells and get mean intensity from selected channels for each WiseRoi from WiseRoi Manager
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) roiManager = new RoiManager();
        ImageStatistics imageStats;
        for (Roi currRoi : roiManager.getRoisAsArray()) {
            resultsTable.incrementCounter();
            resultsTable.addValue("ROI", currRoi.getName());
            for (ImagePlus currChannel : channel.keySet()) {
                currChannel.setRoi(currRoi);
                ArrayList<Point> peaks = runITCN(currChannel, channel.get(currChannel));
                resultsTable.addValue("count (" + currChannel.getTitle() + ")", peaks.size());

                imageStats = ImageStatistics.getStatistics(currRoi.getImage().getProcessor(), 0, currRoi.getImage().getCalibration());
                resultsTable.addValue("roi mean (" + currChannel.getTitle() + ")", imageStats.mean);

                double mean = mean((byte[])currChannel.getProcessor().getPixels(), peaks);
                resultsTable.addValue("peak mean (" + currChannel.getTitle() + ")", mean);
            }

            //ratio
            if(channel.size() == 2) {
                int row = resultsTable.getCounter() - 1;
                resultsTable.addValue("Count Ratio (%)", ratio(resultsTable.getValueAsDouble(1, row), resultsTable.getValueAsDouble(4, row)));
                resultsTable.addValue("rio mean Ratio (%)", ratio(resultsTable.getValueAsDouble(2, row), resultsTable.getValueAsDouble(5, row)));
                resultsTable.addValue("peak mean Ratio (%)", ratio(resultsTable.getValueAsDouble(3, row), resultsTable.getValueAsDouble(6, row)));
            } else if (channel.size() == 3) {
                int row = resultsTable.getCounter() - 1;

                String major = "(red)";
                String minor1 = "(green)";
                String minor2 = "(blue)";
                if (total == 1) {
                    major = "(green)";
                    minor1 = "(red)";
                }
                if (total == 2) {
                    major = "(blue)";
                    minor2 = "(red)";
                }

                //count ratio
                resultsTable.addValue("count " + minor1 + ":" + major
                        , ratio(resultsTable.getValue("count " + minor1, row), resultsTable.getValue("count " + major, row)));
                resultsTable.addValue("count " + minor2 + ":" + major
                        , ratio(resultsTable.getValue("count " + minor2, row), resultsTable.getValue("count " + major, row)));
                //intensity ratio
                resultsTable.addValue("intensity " + minor1 + ":" + major
                        , ratio(resultsTable.getValue("mean " + minor1, row), resultsTable.getValue("mean " + major, row)));
                resultsTable.addValue("intensity " + minor2 + ":" + major
                        , ratio(resultsTable.getValue("mean " + minor2, row), resultsTable.getValue("mean " + major, row)));

            }

            resultsTable.addResults();
            resultsTable.updateResults();
        }


        //Create and collect result images
        ArrayList<ImagePlus> resultImages = new ArrayList<ImagePlus>();
        for (ImagePlus currChannel : channel.keySet()) {
            resultImages.add(new ImagePlus("Results " + currChannel.getTitle(), channel.get(currChannel)));
        }

        for (ImagePlus currImage : resultImages) {
            currImage.show();
        }

        roiManager.runCommand(image, "Show All");

//        String strFrame = "Spheroid RGB " + version + " (" + image.getTitle() + ")";
//        resultsTable.show(strFrame); //results should only shown in the Results window
    }

    /**
     * Every Channel = 8bit ImagePlus and points to an RGB ImageProcessor for the result image
     */
    private HashMap<ImagePlus, ImageProcessor> initChannelMap() {
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
        return channel;
    }

    /**
     * @param c1 component 1
     * @param c2 component 2
     * @return percentage with assumption that grater value is 100%
     */
    private double ratio(double c1, double c2) {
          return (Math.min(c1,c2) / Math.max(c1, c2)) * 100;
    }

    private ArrayList<Point> runITCN(ImagePlus imp, ImageProcessor ipResults) {
        //min distance = cell width / 2 as recommended AND maskImp = null (ROI)
        ITCN_Runner itcn;
        itcn = new ITCN_Runner(imp, cellWidth, (double) cellWidth / 2., threshold, darkPeaks, null);
        itcn.run();

        ArrayList<Point> peaks = itcn.getPeaks();

        //draw peaks
        ipResults.setColor(PEAKS_COLOR);
        ipResults.setLineWidth(1);

        Point pt;
        for (int i = 0; i < peaks.size(); i++) {
            pt = peaks.get(i);

            ipResults.drawDot(pt.x, pt.y);

//            System.out.println("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
        }

        ipResults.setColor(ROI_COLOR);
        imp.getRoi().drawPixels(ipResults);

        return peaks;
    }

    private double mean(byte[] pixels, ArrayList<Point> peaks) {
        double sum = 0;
        for (Point p : peaks) {
            int pos = p.y * width + p.x;
            sum += pixels[pos] & 0xff;;
        }
        return sum / peaks.size();
    }

    // check if Image is RGB
    private void process() {
        int type = image.getType();
        if (type == ImagePlus.GRAY8) {
            rChannel = image;
            PEAKS_COLOR = Color.RED;
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
        }
        if (takeG) {
            gChannel = rgb[1];
            gChannel.setLut(LUT.createLutFromColor(Color.GREEN));
        }
        if (takeB) {
            bChannel = rgb[2];
            bChannel.setLut(LUT.createLutFromColor(Color.BLUE));
        }
    }

    /********************************************************
     * 														*
     *						GUI-METHODS						*
     *														*
     ********************************************************/

    private void createGUI() {
        frame = new PlugInFrame(TITLE);


    }
}
