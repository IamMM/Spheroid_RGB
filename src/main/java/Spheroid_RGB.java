/*
 * To the extent possible under law, the Fiji developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.ChannelSplitter;
import ij.process.ImageProcessor;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;

/**
 * ProcessPixels
 *
 * A template for processing each pixel of either
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
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

	// plugin parameters
	private boolean takeR;
	private boolean takeG;
	private boolean takeB;

	/**
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
    public void run(String arg) {
        if (IJ.versionLessThan("1.36b")) return;

        image = WindowManager.getCurrentImage();
        ImageProcessor ip = image.getProcessor();

        if (image!=null) {
            Roi roi = image.getRoi();
        }

        if (showDialog()) {
            process(ip);
            image.updateAndDraw();
        }

        runApplication(image.getTitle());
	}

	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("Spheroid RGB");

		// default value is 0.00, 2 digits right of the decimal point
//		gd.addNumericField("value", 0.00, 2);
//		gd.addStringField("name", "John");

		String[] lables = new String[]{"R", "G", "B"};
		boolean[] defaultValues = new boolean[]{true, true, true};

		gd.addMessage("Choose Color Channels");
		gd.addCheckboxGroup(3, 1, lables, defaultValues);

		gd.showDialog();
		if (gd.wasCanceled() )
			return false;

		// get entered values
		takeR = gd.getNextBoolean();
		takeG = gd.getNextBoolean();
		takeB = gd.getNextBoolean();

		return true;
	}

    private void runApplication(String name) {
        // create window/frame
        String strFrame = "Spheroid RGB " + version + " (" + name +")";

    }

	/**
	 * Process an image.
	 *
	 * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does require it;
	 * the method {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only
	 * handle 2-dimensional data.
	 *
	 * If your plugin does not change the pixels in-place, make this method return the results and
	 * change the {@link #setup(java.lang.String, ij.ImagePlus)} method to return also the
	 * <i>DOES_NOTHING</i> flag.
	 *
	 * @param image the image (possible multi-dimensional)
	 */
	public void process(ImagePlus image) {
		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= image.getStackSize(); i++)
			process(image.getStack().getProcessor(i));
	}

	// Select processing method depending on image type
	public void process(ImageProcessor ip) {
		int type = image.getType();
		if (type == ImagePlus.GRAY8)
			process( (byte[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY16)
			process( (short[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY32)
			process( (float[]) ip.getPixels() );
		else if (type == ImagePlus.COLOR_RGB) {
            splitChannels(image);
//            process((int[]) ip.getPixels());
        } else {
			throw new RuntimeException("not supported");
		}
	}

	// processing of GRAY8 images
	public void process(byte[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += 0;
			}
		}
	}

	// processing of GRAY16 images
	public void process(short[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += 0;
			}
		}
	}

	// processing of GRAY32 images
	public void process(float[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += 0;
			}
		}
	}

	// processing of COLOR_RGB images
	public void process(int[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += 0;
			}
		}
	}

	//split channels
	public void splitChannels(ImagePlus imp){
        ChannelSplitter splitter = new ChannelSplitter();
        ImagePlus[] rgb = splitter.split(imp);

        if(takeR){
            rgb[0].show();
        }if(takeG){
            rgb[1].show();
        }if(takeB){
            rgb[2].show();
        }

	}

	public void showAbout() {
		IJ.showMessage("Spheroid RGB",
			"a plugin for analysing a stained image of an spheroid"
		);
	}

	/**
	 * Main method for debugging.
	 *
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
}
