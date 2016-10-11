import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.measure.Calibration;
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
    private int width;

    // nuclei counter values
    private int cellWidth;
    private int threshold;
    private double doubleThreshold;
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

    // magic selection
    private double startTolerance = 128;
    private int startMode = 1;

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
        }else {
            IJ.showMessage("No images open");
            return;
        }

//        if (showDialog()) {
//            process();
//            runApplication();
//        }

        createGUI();
    }

    private boolean showDialog() {
        if(RoiManager.getInstance() == null) {
//            IJ.showMessage("Please select ROI and add to ROI Manager");
            new RoiManager();
        }

        NonBlockingGenericDialog gd = new NonBlockingGenericDialog(TITLE + " " + VERSION);

        gd.addMessage("Image: " + image.getTitle());
        gd.addNumericField("Cell width", 15.00, 0);
        gd.addSlider("Threshold", 0, 255, 5);
        gd.addCheckbox("Count dark peaks", false);

        String[] labels = new String[]{"R", "G", "B"};
        boolean[] defaultValues = new boolean[]{true, false, true};

        gd.addMessage("Choose color channels");
        gd.addCheckboxGroup(3, 1, labels, defaultValues);

        gd.addChoice("Channel representing total number of cells", labels, labels[2]);

        Button magicButton = new Button("magic select");
        magicButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMagicSelectDialog();
            }
        });
        gd.add(magicButton);

        gd.showDialog();

        if (gd.wasCanceled()) return false;
        if (gd.wasOKed() && (RoiManager.getInstance().getCount() == 0)) {
            IJ.showMessage("Roi Manager is empty.");
            showDialog();
        }

        // get entered values
        cellWidth = (int) gd.getNextNumber();
        threshold = (int) gd.getNextNumber();
        darkPeaks = gd.getNextBoolean();

        takeR = gd.getNextBoolean();
        takeG = gd.getNextBoolean();
        takeB = gd.getNextBoolean();

        total = gd.getNextChoiceIndex();

        // 0 - 255 threshold in threshold for nuclei counter 0.0 - 10.0
        doubleThreshold = 10 * ((double)threshold /255);
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

        //count cells and get meanPeak intensity from selected channels for each WiseRoi from WiseRoi Manager
        RoiManager roiManager = RoiManager.getInstance();
        ImageStatistics imageStats;
        Calibration calibration = image.getCalibration();
        for (Roi currRoi : roiManager.getRoisAsArray()) {
            resultsTable.incrementCounter();
            resultsTable.addValue("ROI", currRoi.getName());
            for (ImagePlus currChannel : channel.keySet()) {
                currChannel.setRoi(currRoi);
                ArrayList<Point> peaks = rumNucleiCounter(currChannel, channel.get(currChannel));
                resultsTable.addValue("count (" + currChannel.getTitle() + ")", peaks.size());

                double thresholdMean = meanWithThreshold(currRoi.getImage().getProcessor());
                resultsTable.addValue("mean (" + currChannel.getTitle() + ")", thresholdMean);

                double mean = meanPeak((byte[])currChannel.getProcessor().getPixels(), peaks);
                resultsTable.addValue("mean peak (" + currChannel.getTitle() + ")", mean);
            }

            imageStats = ImageStatistics.getStatistics(currRoi.getImage().getProcessor(), 0, calibration);
            resultsTable.addValue("area (" + calibration.getUnit() + ")", imageStats.area);

            //ratio
            if(channel.size() == 2) {
                int row = resultsTable.getCounter() - 1;
                resultsTable.addValue("count ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(1, row), resultsTable.getValueAsDouble(4, row)));
                resultsTable.addValue("mean ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(2, row), resultsTable.getValueAsDouble(5, row)));
                resultsTable.addValue("mean peak ratio (%)", ratioMinMax(resultsTable.getValueAsDouble(3, row), resultsTable.getValueAsDouble(6, row)));
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

                //count ratioMinMax
                resultsTable.addValue("count " + minor1 + ":" + major
                        , (resultsTable.getValue("count " + minor1, row) / resultsTable.getValue("count " + major, row)) * 100);
                resultsTable.addValue("count " + minor2 + ":" + major
                        , (resultsTable.getValue("count " + minor2, row) / resultsTable.getValue("count " + major, row)) * 100);
                //intensity ratioMinMax
                resultsTable.addValue("intensity " + minor1 + ":" + major
                        , (resultsTable.getValue("mean " + minor1, row) / resultsTable.getValue("mean " + major, row)) * 100);
                resultsTable.addValue("intensity " + minor2 + ":" + major
                        , (resultsTable.getValue("mean " + minor2, row) / resultsTable.getValue("mean " + major, row)) * 100);

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
    private double ratioMinMax(double c1, double c2) {
          return (Math.min(c1,c2) / Math.max(c1, c2)) * 100;
    }

    private void showMagicSelectDialog() {
        IJ.setTool("Point");
        if(image.getRoi() == null) {
            IJ.showMessage("Please select a seed with the point tool");
            return;
        }
        try {
            final PointRoi p = (PointRoi) image.getRoi();
            final Polygon polygon = p.getPolygon();
            final String[] modes = {"Legacy", "4-connected", "8-connected"};

            DialogListener listener = new DialogListener() {
                double tolerance;

                @Override
                public boolean dialogItemChanged(GenericDialog genericDialog, AWTEvent awtEvent) {
                    tolerance = genericDialog.getNextNumber();
                    startTolerance = tolerance;
                    startMode = genericDialog.getNextChoiceIndex();
                    IJ.doWand(polygon.xpoints[0], polygon.ypoints[0], tolerance, modes[startMode]);

                    return true;
                }
            };

            GenericDialog wandDialog = new GenericDialog(TITLE + " magic select");
            wandDialog.addSlider("Tolerance ", 0.0, 255.0, startTolerance);
            wandDialog.addChoice("Mode:", modes, modes[startMode]);
            wandDialog.addDialogListener(listener);
            wandDialog.setOKLabel("Add to Roi Manager");
            IJ.doWand(polygon.xpoints[0], polygon.ypoints[0], startTolerance, modes[startMode]);
            wandDialog.showDialog();
            if (wandDialog.wasOKed()) RoiManager.getInstance().addRoi(image.getRoi());
        }catch (Exception e){
            IJ.showMessage("Selection must be a point selection");
        }
    }

    private ArrayList<Point> rumNucleiCounter(ImagePlus imp, ImageProcessor ipResults) {
        //min distance = cell width / 2 as recommended AND maskImp = null (ROI)
        Nuclei_Counter nucleiCounter = new Nuclei_Counter(imp, cellWidth, (double) cellWidth / 2., doubleThreshold , darkPeaks, null);
        nucleiCounter.run();
        ArrayList<Point> peaks = nucleiCounter.getPeaks();

        drawPeaks(imp, ipResults, peaks);

        return peaks;
    }

    private void drawPeaks(ImagePlus imp, ImageProcessor ipResults, ArrayList<Point> peaks) {
        ipResults.setColor(PEAKS_COLOR);
        ipResults.setLineWidth(1);

        for (Point p : peaks) {
            ipResults.drawDot(p.x, p.y);
//            System.out.println("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
        }

        ipResults.setColor(ROI_COLOR);
        imp.getRoi().drawPixels(ipResults);
    }

    private double meanPeak(byte[] pixels, ArrayList<Point> peaks) {
        double sum = 0;
        for (Point p : peaks) {
            int pos = p.y * width + p.x;
            sum += pixels[pos] & 0xff;
        }
        return sum / peaks.size();
    }

    private double meanWithThreshold (ImageProcessor ip) {
        int[] histogram = ip.getHistogram();
        double sum = 0;
        int minThreshold = 0;
        int maxThreshold= 255;

        if(darkPeaks) maxThreshold -= threshold;
        else minThreshold = threshold;

        for(int i = minThreshold; i <= maxThreshold; i++) {
            sum += (double)i * (double)histogram[i];
        }

        //todo: check with jacqui if all pixels or only range for dividing
        long longPixelCount = 0;
        for(int count : histogram) {
            longPixelCount += (long)count;
        }

        return  sum / (double)longPixelCount;
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
//        gridBagConstraints.ipadx = ipadx;
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

        okButton.setLabel("Analyze");
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(okButton);

        exitButton.setLabel("Cancel");
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
