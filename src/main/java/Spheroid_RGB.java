import ij.*;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.AutoThresholder;
import ij.process.ImageStatistics;
import ij.process.LUT;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
 * Created on october 2016
 *
 * @author Maximlian Maske
 */
public class Spheroid_RGB implements PlugIn, ImageListener {
    // swing components
    private JFrame frame;
    private JPanel mainPanel;
    private JComboBox<String> imgList;
    private JButton openButton;
    private JButton magicSelectButton;
    private JButton showChannelsButton;
    private JCheckBox redCheckBox;
    private JCheckBox greenCheckBox;
    private JCheckBox blueCheckBox;
    private JComboBox<String> totalComboBox;
    private JCheckBox countCellsCheckBox;
    private JCheckBox meanCheckBox;
    private JCheckBox areaCheckBox;
    private JCheckBox integratedDensityCheckBox;
    private JPanel innerCountPanel;
    private JTextField cellWidthField;
    private JTextField minDistField;
    private JButton lineLengthButton;
    private JButton lineLengthButtonMinDist;
    private JCheckBox darkPeaksCheck;
    private JSlider thresholdSlider;
    private JLabel thresholdLabel;
    private JButton maximumButton;
    private JComboBox<String> autoThresholdComboBox;
    private JButton analyzeButton;
    private JCheckBox showLines;
    private JSlider profileSlider;
    private JLabel lineLengthLabel;
    private JLabel profileLabel;
    private JSlider profileLengthSlider;
    private JButton radiusButton;
    private JCheckBox showSelectedChannel;
    private JCheckBox showAllGrayPlots;
    private JButton plotButton;
    private JCheckBox autoScaleCheckBox;
    private JTextField yAxisTextField;
    private JCheckBox cleanTableCheckBox;
    private JCheckBox ratioMeanCheckBox;
    private JCheckBox valueRatiosCheckBox;
    private JButton minButton;

    // constants
    private static final String TITLE = "Spheroid RGB";
    private static final String VERSION = " v0.6.0 ";
    static Color PEAKS_COLOR = Color.WHITE;
    static final Color ROI_COLOR = Color.YELLOW;

   // imageJ components
    static ImagePlus image;
    static RoiManager roiManager;

    // nuclei counter values
    static int cellWidth;
    static double minDist;
    static int threshold;
    static double doubleThreshold;
    static boolean darkPeaks;

    // rgb channels
    private Table_Analyzer table_analyzer;
    static ImagePlus[] rgb;
    static boolean takeR;
    static boolean takeG;
    static boolean takeB;
    static boolean imageIsGray;

    // magic selection
    private double startTolerance = 128;
    private int startMode = 1;

    // multi plot
    private Multi_Plot multiPlot;
    private boolean radius = true;
    private int yMax;


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
        ImagePlus image = IJ.openImage("img/test.png");
//        ImagePlus image = IJ.openImage("img/SN33267.tif");
//        ImagePlus image = IJ.openImage("img/EdU.tif");
        image.show();

        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

    /**
     * @see ij.plugin.PlugIn#run(String)
     */
    @Override
    public void run(String arg) {
        ImagePlus.addImageListener(this);

        if(WindowManager.getImageCount() == 0) {
            IJ.showMessage("no image open");
            return;

//            URL url = null; //todo open example image
//            try {
//                url = getClass().getClassLoader().getResource("img/EdU.tif");
//                Image image = Toolkit.getDefaultToolkit().getImage(url);
//                ImagePlus imp = new ImagePlus("/img/EdU.jpg", image);
//                imp.show();
//            }catch (Exception e) {
//                String msg = e.getMessage();
//                if (msg==null || msg.equals(""))
//                    msg = "" + e;
//                IJ.showMessage("Spheroid RGB", msg + "\n \n" + url);
//                return;
//            }
        }

        if(RoiManager.getInstance() == null) {
            new RoiManager();
        }

        initComponents();
        initImageList();
        initActionListeners();
        setImage();

        frame = new JFrame(TITLE + VERSION);
        frame.setContentPane(this.mainPanel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null); //center the frame on screen
        setLookAndFeel(frame);
        setIcon();
        frame.setVisible(true);
        WindowManager.addWindow(frame);
    }

    private void runAnalyzer() {
        getCountAndMeanValues();

        if (table_analyzer == null) table_analyzer = new Table_Analyzer();

        roiManager = RoiManager.getInstance();
        if (roiManager == null) roiManager = new RoiManager();

        // check if we got what we need
        if(WindowManager.getCurrentImage() == null || image == null) {
            IJ.showMessage("No images open");
            return;
        }
        if(!(takeR || takeG || takeB)) {
            IJ.showMessage("Nothing to do", "No Channel selected.");
            return;
        }
        if (roiManager.getCount() == 0) {
            Roi currRoi = image.getRoi();
            if(currRoi != null) {
                roiManager.addRoi(currRoi);
            }
            else {
                IJ.showMessage("Nothing to do", "Roi Manager is empty.");
                return;
            }
        }

        boolean[] options = new boolean[]
                {cleanTableCheckBox.isSelected(), countCellsCheckBox.isSelected(), meanCheckBox.isSelected(), areaCheckBox.isSelected(),
                        integratedDensityCheckBox.isSelected(), ratioMeanCheckBox.isSelected(),
                        valueRatiosCheckBox.isSelected()};

        table_analyzer.run(image, options, (String) totalComboBox.getSelectedItem());
    }

    private void runMultiPlot() {
        getPlotValues();

        if(multiPlot == null) multiPlot = new Multi_Plot();

        // check if we got what we need
        if(!(takeR || takeG || takeB)) {
            IJ.showMessage("Nothing to do", "No Channel selected.");
            return;
        }

        // check image type (color or not) all supported
        ArrayList<ImagePlus> channel = new ArrayList<>();
        if (image.getType() == ImagePlus.COLOR_RGB) {
            ImagePlus[] rgb = ChannelSplitter.split(image);
            setChannelLut(rgb);
            if (takeR) channel.add(rgb[0]);
            if (takeG) channel.add(rgb[1]);
            if (takeB) channel.add(rgb[2]);
        } else {
            channel.add(image);
        }
        boolean[] options = new boolean[]{cleanTableCheckBox.isSelected(), showLines.isSelected(),
                showSelectedChannel.isSelected(), showAllGrayPlots.isSelected(), autoScaleCheckBox.isSelected()};
        multiPlot.run(channel, image, profileSlider.getValue(), radius, profileLengthSlider.getValue(), (String) totalComboBox.getSelectedItem(), yMax, options);
    }

    // check if Image is RGB or 8bit
    private boolean checkImageType() {
        int type = image.getType();

        switch (type){
            case ImagePlus.GRAY8:
                imageIsGray = true;
                PEAKS_COLOR = Color.RED;
                return true;
            case ImagePlus.GRAY16:
                IJ.showMessage("16-bit gray scale image not supported");
                return false;
            case ImagePlus.GRAY32:
                IJ.showMessage("32-bit gray scale image not supported");
                return false;
            case ImagePlus.COLOR_RGB:
                imageIsGray = false;
                rgb = ChannelSplitter.split(image);
                setChannelLut(rgb);
                return true;
            default: IJ.showMessage("not supported");
                return false;
        }
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
            wandDialog.setCancelLabel("Exit");
            IJ.doWand(polygon.xpoints[0], polygon.ypoints[0], startTolerance, modes[startMode]);
            wandDialog.showDialog();
            if (wandDialog.wasOKed()) RoiManager.getInstance().addRoi(image.getRoi());
        }catch (Exception e){
            IJ.showMessage("Selection must be a point selection");
        }
    }

    // set suitable look up table for 8bit channel (pseudo color)
    private void setChannelLut(ImagePlus[] rgb) {
        rgb[0].setLut(LUT.createLutFromColor(Color.RED));
        rgb[1].setLut(LUT.createLutFromColor(Color.GREEN));
        rgb[2].setLut(LUT.createLutFromColor(Color.BLUE));
    }

    /********************************************************
     * 														*
     *						BUTTON-METHODS  				*
     *														*
     ********************************************************/

    private void setImage() {
        image = WindowManager.getImage(imgList.getItemAt(imgList.getSelectedIndex()));

        if(image == null) {
            initImageList();
            image = WindowManager.getCurrentImage();
        }

        if(imgList.getItemCount() != 0) {
            WindowManager.setCurrentWindow(WindowManager.getImage((String) imgList.getSelectedItem()).getWindow());
            WindowManager.toFront(WindowManager.getFrame(WindowManager.getCurrentImage().getTitle()));
        } else {
            close();
        }
    }

    private void openButtonAction() {
        OpenDialog od = new OpenDialog("Open..", "");
        String directory = od.getDirectory();
        String name = od.getFileName();
        if (name == null) return;

        Opener opener = new Opener();
        image = opener.openImage(directory, name);
        image.show();
    }

    private void showSelectedChannels() {
        if (image.getType() == ImagePlus.COLOR_RGB) {
            ImagePlus[] split = ChannelSplitter.split(image);
            setChannelLut(split);
            if (takeR) split[0].show();
            if (takeG) split[1].show();
            if (takeB) split[2].show();
        }
    }

    private void widthButtonAction(JTextField field) {
        Roi roi = image.getRoi();

        if (roi.isLine()) {
            Line line = (Line) roi;
            field.setText(Integer.toString((int) Math.ceil(line.getRawLength())));
        }
    }

    private void cellWidthFieldChanged() {
        String input = cellWidthField.getText();
        if(input.isEmpty()) minDistField.setText("0.0");
        else {
            String clean = input.replaceAll("[^\\d.]", ""); // http://www.regular-expressions.info/shorthand.html
            minDistField.setText(Double.toString(Double.parseDouble(clean) / 2));
        }
    }

    private void maximumButtonAction() {
        Roi roi = image.getRoi();
        int max = 0;
        if (roi != null)
        if (roi.isArea()) {
            ImageStatistics stats = roi.getImage().getStatistics();
            max = ((int) Math.ceil(stats.max));
            if (image.getType() == ImagePlus.COLOR_RGB) {
                ImagePlus[] split = ChannelSplitter.split(image);
                for (ImagePlus channel : split) {
                    channel.setRoi(roi);
                    roi = channel.getRoi();
                    stats = roi.getImage().getStatistics();
                    int channelMax = (int) Math.ceil(stats.max);
                    if (channelMax > max) max = channelMax;
                }
            }
            thresholdSlider.setValue(max);
        }

    }

    private void minimumButtonAction() {
        Roi roi = image.getRoi();

        if (roi != null)
            if (roi.isArea()) {
                ImageStatistics stats = roi.getImage().getStatistics();
                thresholdSlider.setValue((int) Math.ceil(stats.min));
            }
    }

    private void autoThresholdAction(String method) {
        if(image != null) {
            AutoThresholder autoThresholder = new AutoThresholder();
            int auto = autoThresholder.getThreshold(method, image.getProcessor().getHistogram());
            thresholdSlider.setValue(auto);
        }
    }

    private void analyzeButtonActionPerformed() {
        if(checkImageType())
            runAnalyzer();
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

        imgList.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setImage();
            }
        });

        countCellsCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (Component c : innerCountPanel.getComponents()) {
                    c.setEnabled(countCellsCheckBox.isSelected());
                }
            }
        });

        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                analyzeButtonActionPerformed();
            }
        });

        cellWidthField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
                cellWidthFieldChanged();
            }
        });

        lineLengthButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                widthButtonAction(cellWidthField);
                cellWidthFieldChanged();
            }
        });

        lineLengthButtonMinDist.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                widthButtonAction(minDistField);
            }
        });

        thresholdSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                thresholdLabel.setText(thresholdSlider.getValue() + "");
            }
        });
        magicSelectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMagicSelectDialog();
            }
        });
        showChannelsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSelectedChannels();
            }
        });
        maximumButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                maximumButtonAction();
            }
        });
        minButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                minimumButtonAction();
            }
        });

        autoThresholdComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                autoThresholdAction((String) autoThresholdComboBox.getSelectedItem());
            }
        });

        darkPeaksCheck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                darkPeaks = darkPeaksCheck.isSelected();
                thresholdSlider.setInverted(darkPeaks);
            }
        });

        redCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeR = redCheckBox.isSelected();
                totalComboBox.setEnabled(takeR ? (takeG || takeB) : (takeG && takeB));
            }
        });

        greenCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeG = greenCheckBox.isSelected();
                totalComboBox.setEnabled(takeR ? (takeG || takeB) : (takeG && takeB));
            }
        });

        blueCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeB = blueCheckBox.isSelected();
                totalComboBox.setEnabled(takeR ? (takeG || takeB) : (takeG && takeB));
            }
        });

        //Multi plot
        profileSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                profileLabel.setText(profileSlider.getValue() + " lines");
            }
        });

        showLines.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                image.setHideOverlay(!showLines.isSelected());
            }
        });

        autoScaleCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                yAxisTextField.setEnabled(!autoScaleCheckBox.isSelected());
            }
        });

        plotButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(image.getRoi() == null) {
                    IJ.showMessage("Nothing to do.", "No active ROI selected");
                } else {
                    runMultiPlot();
                }
            }
        });

        radiusButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(radius){
                    radius = false;
                    radiusButton.setText("diameter");
                }else {
                    radius = true;
                    radiusButton.setText("radius");
                }
            }
        });

        profileLengthSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int value = profileLengthSlider.getValue();
                if (value > 0) lineLengthLabel.setText("+" + value + " %");
                else lineLengthLabel.setText(value + " %");
            }
        });

        lineLengthLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                profileLengthSlider.setValue(0);
            }
        });

    }

    private void getCountAndMeanValues() {
        cellWidth = Integer.parseInt(cellWidthField.getText().replaceAll("[^\\d.]", "")); //make sure there are only digits
        minDist = Double.parseDouble(minDistField.getText().replace("[^\\d.]", "")); //.replaceAll("\\D", "")
        threshold = thresholdSlider.getValue();
        doubleThreshold = 10 * ((double)threshold /255);
    }

    private void getPlotValues() {
        if (!autoScaleCheckBox.isSelected()) yMax = Integer.parseInt(yAxisTextField.getText().replaceAll("[^\\d.]", "")); //make sure there are only digits
    }

    private void setLookAndFeel(JFrame frame) {
        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setIcon() {
        ImageIcon img = new ImageIcon("icon/icon_SN33267.png");
        frame.setIconImage(img.getImage());
    }

    private void initImageList() {
        String[] titles = WindowManager.getImageTitles();
        imgList.removeAllItems();
        for (String title : titles) {
            imgList.addItem(title);
        }
    }

    private void initComponents() {
        // initialize total combo box
        totalComboBox.addItem("Red");
        totalComboBox.addItem("Green");
        totalComboBox.addItem("Blue");
        totalComboBox.setSelectedIndex(2);

        // initialize auto threshold combo box
        String[] methods = AutoThresholder.getMethods();
        for (String method : methods) {
            autoThresholdComboBox.addItem(method);
        }

        // disable all count related components
        for (Component c : innerCountPanel.getComponents()) {
            c.setEnabled(false);
        }
    }

    private void close() {
        WindowManager.removeWindow(this.frame);
        this.frame.dispose();
    }

    @Override
    public void imageOpened(ImagePlus imagePlus) {
        imgList.addItem(imagePlus.getTitle());
        imgList.setSelectedIndex(imgList.getItemCount() - 1);
        image = imagePlus;
    }

    @Override
    public void imageClosed(ImagePlus imagePlus) {
        if(WindowManager.getImageCount() == 0) close();
        else imgList.removeItem(imagePlus.getTitle());
    }

    @Override
    public void imageUpdated(ImagePlus imagePlus) {

    }
}