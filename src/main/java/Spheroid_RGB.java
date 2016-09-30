import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.Line;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.io.Opener;
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
import java.awt.event.*;
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

    private java.awt.Choice maskChoice;
    private java.awt.TextField minDistTextField;
    private java.awt.TextField widthTextField;
    private java.awt.TextField thresTextField;
    private java.awt.Scrollbar thresScroll;
    private ArrayList winIDList;

    private static final String strNONE = "Use selected ROI";

    // imageJ components
    private ImagePlus image;
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
        if (IJ.versionLessThan("1.36b")) return;
        //old gui
//        if(WindowManager.getCurrentImage() != null) {
//            image = WindowManager.getCurrentImage();
//        }else {
//            IJ.showMessage("No images open");
//            return;
//        }
//
//        if (showDialog()) {
//            process();
//            runApplication();
//        }

        //new gui
        createGUI();

    }

    private boolean showDialog() {
        if(RoiManager.getInstance() == null) {
            IJ.showMessage("Please select ROI and add to ROI Manager");
            new RoiManager();
        }

        NonBlockingGenericDialog gd = new NonBlockingGenericDialog("Spheroid RGB (" + image.getTitle() + ")");

//        gd.setAlwaysOnTop(true);
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
                int numberOfCells = runITCN(currChannel, channel.get(currChannel));
                resultsTable.addValue("count (" + currChannel.getTitle() + ")", numberOfCells);

                imageStats = ImageStatistics.getStatistics(currRoi.getImage().getProcessor(), 0, currRoi.getImage().getCalibration());
                resultsTable.addValue("mean (" + currChannel.getTitle() + ")", imageStats.mean);
            }

            //ratio
            if(channel.size() == 2) {
                int row = resultsTable.getCounter() - 1;
                resultsTable.addValue("Count Ratio (%)", ratio(resultsTable.getValueAsDouble(1, row), resultsTable.getValueAsDouble(3, row)));
                resultsTable.addValue("Intensity Ratio (%)", ratio(resultsTable.getValueAsDouble(2, row), resultsTable.getValueAsDouble(4, row)));
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

    private int runITCN(ImagePlus imp, ImageProcessor ipResults) {
        //min distance = cell width / 2 as recommended AND maskImp = null (ROI)
        ITCN_Runner itcn;
        itcn = new ITCN_Runner(imp, cellWidth, (double) cellWidth / 2., threshold, darkPeaks, null);
        itcn.run();

        int numberOfCells = itcn.getNumberOfCells();
        ArrayList<Point> peaks = itcn.getPeaks();

        //draw peaks
        ipResults.setColor(PEAKS_COLOR);
        ipResults.setLineWidth(1);

        Point pt;
        for (int i = 0; i < numberOfCells; i++) {
            pt = peaks.get(i);

            ipResults.drawDot(pt.x, pt.y);

//            System.out.println("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
        }

//        ipResults.setColor(ROI_COLOR);
//        imp.getRoi().drawPixels(ipResults);

        return numberOfCells;
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

        Panel imagePanel = new Panel();
        Label ccImageLabel = new Label();
        Label filenameLabel = new Label();
        Panel varsPanel = new Panel();
        Label widthLabel = new Label();
        widthTextField = new TextField();
        Label minDistUnitsLabel = new Label();
       Label minDistLabel = new Label();
        minDistTextField = new TextField();
        Label widthUnitsLabel = new Label();
        Label recomendLabel = new Label();
        Checkbox darkPeaksCheckbox = new Checkbox();
        Panel maskPanel = new Panel();
        Label maskLabel = new Label();
        maskChoice = new Choice();
        Button openMaskButton = new Button();
        Panel buttonPanel = new Panel();
        Button okButton = new Button();
        Button exitButton = new Button();
        Button widthButton = new Button();
        Button minDistButton = new Button();
        Label thresLabel = new Label();
        thresTextField = new TextField();
        thresScroll = new Scrollbar();
        Panel midPanel = new Panel();

        GridBagConstraints gridBagConstraints;
        int ipadx = 50;

        frame.setLayout(new GridLayout(5, 1));

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent evt) { image.unlock(); }
        });


        // window manager stuff.....

        image = WindowManager.getCurrentImage();
        if (image == null) {
            IJ.showStatus("No image");
            return;
        }
        if (!image.lock())
            return;

        int[] WinList = WindowManager.getIDList();
        if (WinList == null) {
            IJ.error("No windows are open.");
            return;
        }

        winIDList = new ArrayList(WinList.length + 1);
        winIDList.add(new Integer(0));
        for (int i = 0; i < WinList.length; i++) {
            winIDList.add(new Integer(WinList[i]));
        }

        String[] WinTitles = new String[WinList.length + 1];
        WinTitles[0] = strNONE;

        // Window Manager stuff...

        imagePanel.setLayout(new GridLayout());

        ccImageLabel.setAlignment(Label.RIGHT);
        ccImageLabel.setText("Image Name:");
        imagePanel.add(ccImageLabel);

        filenameLabel.setText(image.getTitle());
        imagePanel.add(filenameLabel);

        frame.add(imagePanel);

        //varsPanel.setLayout(new java.awt.GridLayout(3, 4));
        varsPanel.setLayout(new GridBagLayout());

        widthLabel.setAlignment(Label.RIGHT);
        widthLabel.setText("Width");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.anchor = GridBagConstraints.EAST;
        varsPanel.add(widthLabel, gridBagConstraints);

//        widthLabel.setAlignment(java.awt.Label.RIGHT);
//        widthLabel.setText("Width");
//        varsPanel.add(widthLabel);

        widthTextField.setText(Integer.toString(widthDefault));
        widthTextField.addTextListener(new TextListener() {
            public void textValueChanged(TextEvent evt) {
                widthTextFieldTextValueChanged(evt);
            }
        });
        //varsPanel.add(widthTextField);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = ipadx;
        varsPanel.add(widthTextField, gridBagConstraints);

        minDistUnitsLabel.setText("pixels");
        varsPanel.add(minDistUnitsLabel);

        widthButton.setLabel("Measure Line Length");
        widthButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                widthButtonActionPerformed(evt);
            }
        });
        //varsPanel.add(widthButton);
        gridBagConstraints = new GridBagConstraints();
        varsPanel.add(widthButton, gridBagConstraints);

        minDistLabel.setAlignment(Label.RIGHT);
        minDistLabel.setText("Minimum Distance");
        varsPanel.add(minDistLabel);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.EAST;
        varsPanel.add(minDistLabel, gridBagConstraints);

        minDistTextField.setText(Double.toString(min_distDefault));
        varsPanel.add(minDistTextField);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = ipadx;
        varsPanel.add(minDistTextField, gridBagConstraints);

        widthUnitsLabel.setText("pixels");
        varsPanel.add(widthUnitsLabel);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        varsPanel.add(widthUnitsLabel, gridBagConstraints);

        minDistButton.setLabel("Measure Line Length");
        minDistButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                minDistButtonActionPerformed(evt);
            }
        });
        varsPanel.add(minDistButton);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        varsPanel.add(minDistButton, gridBagConstraints);

        thresLabel.setAlignment(Label.RIGHT);
        thresLabel.setText("Threshold");
        varsPanel.add(thresLabel);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = GridBagConstraints.EAST;
        varsPanel.add(thresLabel, gridBagConstraints);

        thresTextField.setText(Double.toString(thresDefault));
        thresTextField.addTextListener(new TextListener() {
            public void textValueChanged(TextEvent evt) {
                thresTextFieldTextValueChanged(evt);
            }
        });
        varsPanel.add(thresTextField);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = ipadx;
        varsPanel.add(thresTextField, gridBagConstraints);

        //varsPanel.add(thresScroll);
        thresScroll.setValues((int) (thresPrecision * thresDefault), 1, 0, 10 * (int) thresPrecision + 1);
        thresScroll.setOrientation(Scrollbar.HORIZONTAL);
        thresScroll.addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent evt) {
                thresScrollAdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        varsPanel.add(thresScroll, gridBagConstraints);

        frame.add(varsPanel);

        midPanel.setLayout(new GridLayout(2, 1));

        recomendLabel.setAlignment(Label.CENTER);
        recomendLabel.setText("(Recommended: Minimum Distance = Width/2)");
        midPanel.add(recomendLabel);

        darkPeaksCheckbox.setLabel("Detect Dark Peaks");
        midPanel.add(darkPeaksCheckbox);

        frame.add(midPanel);

        maskLabel.setText("Mask Image");
        maskPanel.add(maskLabel);

        maskPanel.add(maskChoice);
        maskChoice.add(WinTitles[0]);
        for (int i = 0; i < WinList.length; i++) {
            ImagePlus imp = WindowManager.getImage(WinList[i]);
            if (imp != null) {
                WinTitles[i + 1] = imp.getTitle();
            } else {
                WinTitles[i + 1] = "";
            }
            maskChoice.add(WinTitles[i + 1]);
        }

        openMaskButton.setLabel("Open...");
        openMaskButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                openMaskButtonActionPerformed(evt);
            }
        });

        maskPanel.add(openMaskButton);

        frame.add(maskPanel);

        buttonPanel.setLayout(new GridLayout());

        okButton.setLabel("Count");
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(okButton);

        exitButton.setLabel("Exit");
        exitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(exitButton);

        frame.add(buttonPanel);

        frame.pack();
        frame.setSize(400, 375);
        GUI.center(frame);
        frame.setVisible(true);
    }

    private void widthTextFieldTextValueChanged(TextEvent evt) {
        if(widthTextField.getText().isEmpty()) minDistTextField.setText("0.0");
        else minDistTextField.setText(Double.toString(Double.parseDouble(widthTextField.getText()) / 2));
    }

    private void widthButtonActionPerformed(ActionEvent evt) {
        Roi roi = image.getRoi();

        if (roi.isLine()) {
            Line line = (Line) roi;
            widthTextField.setText(Integer.toString((int) Math.ceil(line.getRawLength())));
        }
    }

    private void minDistButtonActionPerformed(ActionEvent evt) {
        Roi roi = image.getRoi();

        if (roi.isLine()) {
            Line line = (Line) roi;
            minDistTextField.setText(Integer.toString((int) Math.ceil(line.getRawLength())));
        }
    }

    private void thresTextFieldTextValueChanged(TextEvent evt) {
        double threshold = Double.parseDouble(thresTextField.getText());
        if (thresPrecision * threshold != Math.round(thresPrecision * threshold)) {
            threshold = Math.round(thresPrecision * threshold) / thresPrecision;
            thresTextField.setText(Double.toString(threshold));
        }
        if ((double) thresScroll.getValue() != thresPrecision * threshold)
            thresScroll.setValue((int) (thresPrecision * threshold));
    }

    private void thresScrollAdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
        double threshold = (double) thresScroll.getValue();
        thresTextField.setText(Double.toString(threshold / thresPrecision));
    }

    private void exitButtonActionPerformed(ActionEvent evt) {
        frame.close();
    }

    private void okButtonActionPerformed(ActionEvent evt) {
       runApplication();
    }

    private void openMaskButtonActionPerformed(ActionEvent evt) {
        OpenDialog od = new OpenDialog("Open Mask...", "");
        String directory = od.getDirectory();
        String name = od.getFileName();
        if (name == null)
            return;

        Opener opener = new Opener();
        ImagePlus imp2 = opener.openImage(directory, name);

        winIDList.add(new Integer(imp2.getID()));

        maskChoice.add(name);
        maskChoice.select(maskChoice.getItemCount() - 1);

        imp2.show();
    }
}
