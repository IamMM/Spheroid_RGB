/**
 * Created on September 20, 2016
 *
 * @author Maximilian Maske
 */

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

/**
 * Spheroid_RGB
 *
 * A plugin for analysing each channel of RGB Image
 *
 * @author The Fiji Team
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

    // what channels to take
    private boolean takeR;
    private boolean takeG;
    private boolean takeB;

    // split channels
    private ImagePlus rChannel;
    private ImagePlus gChannel;
    private ImagePlus bChannel;

    /**
     * Main method for debugging.
     * <p>
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
        IJ.runPlugIn(clazz.getName(), "");
    }

    /**
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(String arg) {
//        if (IJ.versionLessThan("1.36b")) return;

        image = WindowManager.getCurrentImage();
        ImageProcessor ip = image.getProcessor();

        //TODO use ROI
        if (image != null) {
            Roi roi = image.getRoi();
        }

        if (showDialog()) {
            process(ip);
            runApplication(image.getTitle());
        }
    }

    private boolean showDialog() {
        GenericDialog gd = new GenericDialog("Spheroid RGB");

        // default value is 0.00, 2 digits right of the decimal point
        gd.addMessage("Parameter for Automated Cell Count");
        gd.addNumericField("cell width", 15.00, 0);
        gd.addSlider("threshold", 0.0, 1.0, 0.2);

        String[] labels = new String[]{"R", "G", "B"};
        boolean[] defaultValues = new boolean[]{true, true, true};

        gd.addMessage("Choose Color Channels");
        gd.addCheckboxGroup(3, 1, labels, defaultValues);

        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        // get entered values
        cellWidth = (int) gd.getNextNumber();
        threshold = gd.getNextNumber();

        takeR = gd.getNextBoolean();
        takeG = gd.getNextBoolean();
        takeB = gd.getNextBoolean();

        return true;
    }

    private void runApplication(String name) {
        // create window/frame
        String strFrame = "Spheroid RGB " + version + " (" + name + ")";

        if (takeR) {
            new ITCN_Runner(rChannel, cellWidth, (double) cellWidth/2., threshold, false, image);
        }
        if (takeG) {
            new ITCN_Runner(gChannel, cellWidth, (double) cellWidth/2., threshold, false, image);
        }
        if (takeB) {
            new ITCN_Runner(bChannel, cellWidth, (double) cellWidth/2., threshold, false, image);
        }
    }

    // Select processing method depending on image type
    public void process(ImageProcessor ip) {
        int type = image.getType();
//        if (type == ImagePlus.GRAY8)
//            process((byte[]) ip.getPixels());
//        else if (type == ImagePlus.GRAY16)
//            process((short[]) ip.getPixels());
//        else if (type == ImagePlus.GRAY32)
//            process((float[]) ip.getPixels());
//        else

        if (type == ImagePlus.COLOR_RGB) {
            splitChannels(image);
        } else {
            throw new RuntimeException("not supported");
        }
    }

    //split channels
    public void splitChannels(ImagePlus imp) {
        ChannelSplitter splitter = new ChannelSplitter();
        ImagePlus[] rgb = splitter.split(imp);

        if (takeR) {
            rChannel = rgb[0];
            rChannel.show();
        }
        if (takeG) {
            gChannel = rgb[1];
            gChannel.show();
        }
        if (takeB) {
            bChannel = rgb[2];
            bChannel.show();
        }
    }

    public void showAbout() {
        IJ.showMessage("Spheroid RGB",
                "a plugin for analysing a stained image of an spheroid"
        );
    }
}
