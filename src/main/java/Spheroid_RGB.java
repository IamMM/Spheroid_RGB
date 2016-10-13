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
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Maxmilian Maske on october 2016.
 */
public class Spheroid_RGB implements PlugIn {
    // swing components
    private JFrame frame;
    private JComboBox winList;
    private JPanel mainPanel;
    private JButton openButton;
    private JButton lineLengthButton;
    private JSlider thresSlider;
    private JButton maximumButton;
    private JButton magicSelectButton;
    private JCheckBox redCheckBox;
    private JCheckBox blueCheckBox;
    private JCheckBox greenCheckBox;
    private JButton analyzeButton;
    private JButton cancelButton;
    private JLabel thresLabel;
    private JTextField cellWidthField;
    private JTextField minDistField;
    private JCheckBox darkPeaksCheck;
    private JComboBox totalCheckBox;
    private JButton lineLengthButtonMinDist;
    private JPanel totalPanel;

    // constants
    private static final String TITLE = "Spheroid RGB";
    private static final String VERSION = " v1.0 ";
    private static Color PEAKS_COLOR = Color.WHITE;
    private static final Color ROI_COLOR = Color.YELLOW;

   // imageJ components
    private ImagePlus image;
    private int width;

    // nuclei counter values
    private int cellWidth;
    private double minDist;
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

    public Spheroid_RGB() {
        initActionListeners();
        initImageList();
        initTotalChoice();
    }

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

        // open the Spheroid_RGB sample
        ImagePlus image = IJ.openImage("C:/workspace/Spheroid_RGB/EdU_slide2.2.tif");
        image.show();

        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

    /**
     * @see ij.plugin.PlugIn#run(String)
     */
    @Override
    public void run(String arg) {
        if(WindowManager.getCurrentImage() != null) {
            image = WindowManager.getCurrentImage();
            width = image.getWidth();
        }else {
            IJ.showMessage("No images open");
            return;
        }

        if(RoiManager.getInstance() == null) {
            new RoiManager();
        }
        frame = new JFrame(TITLE + VERSION);
        frame.setContentPane(this.mainPanel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null); //center the frame on screen
        setLookAndFeel();
        frame.setVisible(true);
    }

    private void runApplication() {
        getGuiValues();

        //check image type and init channels
        checkImageType();
        HashMap<ImagePlus, ImageProcessor> channel = initChannelMap();

        //create Results table
        ResultsTable resultsTable = Analyzer.getResultsTable();
        if (resultsTable == null) {
            resultsTable = new ResultsTable();
            Analyzer.setResultsTable(resultsTable);
        }


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

    // check if Image is RGB
    private void checkImageType() {
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

    /**
     * Every Channel = 8bit ImagePlus and points to an RGB ImageProcessor for the result image
     */
    private HashMap<ImagePlus, ImageProcessor> initChannelMap() {
        HashMap<ImagePlus, ImageProcessor> channel = new HashMap<ImagePlus, ImageProcessor>();
        if(takeR) {
            ImageProcessor redResults = (rChannel.getProcessor().duplicate()).convertToRGB();
            channel.put(rChannel, redResults);
        }
        if(takeG) {
            ImageProcessor greenResults = (gChannel.getProcessor().duplicate()).convertToRGB();
            channel.put(gChannel, greenResults);
        }
        if(takeB) {
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
        Nuclei_Counter nucleiCounter = new Nuclei_Counter(imp, cellWidth, minDist, doubleThreshold , darkPeaks, null);
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
     *						BUTTON-METHODS  				*
     *														*
     ********************************************************/

    private void openButtonAction() {
        OpenDialog od = new OpenDialog("Open..", "");
        String directory = od.getDirectory();
        String name = od.getFileName();
        if (name == null) return;

        Opener opener = new Opener();
        image = opener.openImage(directory, name);

        image.show();
        winList.addItem(image.getTitle());
        winList.setSelectedIndex(winList.getItemCount() - 1);
    }

    private void widthButtonAction(JTextField field) {
        Roi roi = image.getRoi();

        if (roi.isLine()) {
            Line line = (Line) roi;
            field.setText(Integer.toString((int) Math.ceil(line.getRawLength())));
        }
    }

    private void cellWidthFieldChanged() {
        if(cellWidthField.getText().isEmpty()) minDistField.setText("0.0");
        else minDistField.setText(Double.toString(Double.parseDouble(cellWidthField.getText()) / 2));
    }

    private void maximumButtonAction() {
        Roi roi = image.getRoi();

        if (roi.isArea()) {
            ImageStatistics stats = roi.getImage().getStatistics();
            thresSlider.setValue((int) Math.ceil(stats.max));
        }
    }

    private void okButtonActionPerformed() {
        checkImageType();
        runApplication();
    }

    /********************************************************
     * 														*
     *						GUI-METHODS						*
     *														*
     ********************************************************/

    private void initActionListeners(){

        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openButtonAction();
            }
        });

        winList.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                image = WindowManager.getImage((String) winList.getSelectedItem());
                if (image == null) {
                    initImageList();
                    image = WindowManager.getCurrentImage();
                }
            }
        });

        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okButtonActionPerformed();
            }
        });

        cellWidthField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
//                super.keyTyped(e);
                cellWidthFieldChanged();
            }
        });

        lineLengthButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                widthButtonAction(cellWidthField);
            }
        });

        lineLengthButtonMinDist.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                widthButtonAction(minDistField);
            }
        });

        thresSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                thresLabel.setText(thresSlider.getValue() + "");
            }
        });
        magicSelectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMagicSelectDialog();
            }
        });
        maximumButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                maximumButtonAction();
            }
        });
        darkPeaksCheck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                darkPeaks = darkPeaksCheck.isSelected();
            }
        });

        redCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeR = redCheckBox.isSelected();
                totalPanel.setVisible(takeR && takeG && takeB);
            }
        });

        greenCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeG = greenCheckBox.isSelected();
                totalPanel.setVisible(takeR && takeG && takeB);
            }
        });

        blueCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeB = blueCheckBox.isSelected();
                totalPanel.setVisible(takeR && takeG && takeB);
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });
    }

    private void getGuiValues() {
        cellWidth = Integer.parseInt(cellWidthField.getText());
        minDist = Double.parseDouble(minDistField.getText());
        threshold = thresSlider.getValue();
        doubleThreshold = 10 * ((double)threshold /255);
        takeR = redCheckBox.isSelected();
        takeG = greenCheckBox.isSelected();
        takeB = blueCheckBox.isSelected();
        total = totalCheckBox.getSelectedIndex();
    }

    private void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }

    private void initImageList() {
        String[] titles = WindowManager.getImageTitles();
        winList.removeAllItems();
        for (String title : titles) {
            winList.addItem(title);
        }
    }

    private void initTotalChoice() {
        totalCheckBox.addItem("Red");
        totalCheckBox.addItem("Green");
        totalCheckBox.addItem("Blue");
    }
}
