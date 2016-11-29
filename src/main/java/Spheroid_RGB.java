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
    private JButton autoButton;
    private JPanel greenThresholdPanel;
    private JPanel blueThresholdPanel;
    private JPanel defaultThresholdPanel;
    private JPanel redThresholdPanel;
    private JButton redAutoButton;
    private JButton greenAutoButton;
    private JButton blueAutoButton;
    private JLabel redThresholdLabel;
    private JLabel greenThresholdLabel;
    private JLabel blueThresholdLabel;
    private JSlider redThresholdSlider;
    private JSlider greenThresholdSlider;
    private JSlider blueThresholdSlider;
    private JButton redMaxButton;
    private JButton greenMaxButton;
    private JButton blueMaxButton;
    private JCheckBox plotCountDistanceFunctionCheckBox;
    private JTextField quantificationTextField;
    private JPanel outerCountPanel;
    private JRadioButton starPlotRadioButton;
    private JRadioButton ringPlotRadioButton;
    private JRadioButton convexHullPlotRadioButton;

    // constants
    private static final String TITLE = "Spheroid RGB";
    private static final String VERSION = " v0.7.9";
    Color PEAKS_COLOR = Color.WHITE;
    final Color ROI_COLOR = Color.YELLOW;

   // imageJ components
    ImagePlus image;
    RoiManager roiManager;

    // nuclei counter values
    int cellWidth;
    double minDist;
    boolean darkPeaks;
    int quantification;

    // rgb channels
    private Table_Analyzer table_analyzer;
    ImagePlus[] rgb;
    boolean takeR;
    boolean takeG;
    boolean takeB;
    boolean imageIsGray;

    // magic selection
    private double startTolerance = 128;
    private int startMode = 2; // [0]: "Legacy", [1]: "4-connected", [2]: "8-connected"

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
//        ImagePlus image = IJ.openImage("img/test.png");
//        ImagePlus image = IJ.openImage("img/SN33267.tif");
        ImagePlus image = IJ.openImage("img/EdU.tif");
        image.show();

        // runStarPlot the plugin
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
        // check if we got what we need
        if(WindowManager.getCurrentImage() == null || image == null) {
            IJ.showMessage("No images open");
            return;
        }
        if(!(takeR || takeG || takeB) && !imageIsGray) {
            IJ.showMessage("Nothing to do", "No Channel selected.");
            return;
        }

        getCountAndMeanValues();

        if (table_analyzer == null) table_analyzer = new Table_Analyzer();

        roiManager = RoiManager.getInstance();
        if (roiManager == null) roiManager = new RoiManager();

        if (roiManager.getCount() == 0) {
            Roi currRoi = image.getRoi();
            if(currRoi == null) image.setRoi(0,0,image.getWidth()-1,image.getHeight()-1);
            roiManager.addRoi(currRoi);
        }

        boolean[] options = new boolean[]
                {cleanTableCheckBox.isSelected(), countCellsCheckBox.isSelected(), meanCheckBox.isSelected(), areaCheckBox.isSelected(),
                        integratedDensityCheckBox.isSelected(), ratioMeanCheckBox.isSelected(),
                        valueRatiosCheckBox.isSelected(), plotCountDistanceFunctionCheckBox.isSelected()};

        table_analyzer.run(this, image, options, (String) totalComboBox.getSelectedItem());
    }

    private void runMultiPlot() {
        getPlotValues();

        if(multiPlot == null) multiPlot = new Multi_Plot();

        roiManager = RoiManager.getInstance();
        if (roiManager == null) roiManager = new RoiManager();


        if (image.getRoi() == null) {
            if (roiManager.getCount() > 0) {
                image.setRoi(roiManager.getRoi(0));
            } else {
                IJ.showMessage("Nothing to do.", "No ROI selected");
                return;
            }
        } else if(roiManager.getCount() == 0) roiManager.addRoi(image.getRoi());

        // check image type (color or not) all supported
        ArrayList<ImagePlus> channel = new ArrayList<>();
        if (image.getType() == ImagePlus.COLOR_RGB) {
            // check if we got what we need
            if(!(takeR || takeG || takeB)) {
                IJ.showMessage("Nothing to do", "No Channel selected.");
                return;
            }
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

        if (starPlotRadioButton.isSelected())
            multiPlot.runStarPlot(channel, image, profileSlider.getValue(), radius, profileLengthSlider.getValue(), yMax, options);
        if(ringPlotRadioButton.isSelected())
            multiPlot.runRingPlot(channel, image, yMax, options, profileLengthSlider.getValue());
        if (convexHullPlotRadioButton.isSelected())
            multiPlot.runConvexHullPlot(channel, image, yMax, options, profileLengthSlider.getValue());
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
        if(image.getRoi() == null || image.getRoi().isArea() || image.getRoi().isLine()) {
            IJ.showMessage("Please select a seed with the point tool");
            IJ.setTool("Point");
            return;
        }

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
        if (wandDialog.wasOKed()) {
            RoiManager roiManager = RoiManager.getInstance();
            if(roiManager == null) roiManager = new RoiManager();
            roiManager.addRoi(image.getRoi());
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

    private void maximumButtonAction(ImagePlus image, JSlider slider) {
        Roi roi = image.getRoi();
        ImageStatistics stats;

        if (roi != null && roi.isArea()) stats = roi.getImage().getStatistics();
        else stats = image.getStatistics();

        if(darkPeaks) slider.setValue((int) Math.ceil(stats.min));
        else slider.setValue((int) Math.ceil(stats.max));
    }

    private void autoThresholdAction(String method, ImagePlus image, JSlider slider) {
        if(image != null) {
            AutoThresholder autoThresholder = new AutoThresholder();
            int auto = autoThresholder.getThreshold(method, image.getProcessor().getHistogram());
            slider.setValue(auto);
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
                for (Component c : outerCountPanel.getComponents()) {
                    c.setEnabled(countCellsCheckBox.isSelected());
                }
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

        plotCountDistanceFunctionCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quantificationTextField.setEnabled(plotCountDistanceFunctionCheckBox.isSelected());
            }
        });

        thresholdSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                thresholdLabel.setText(thresholdSlider.getValue() + "");
            }
        });
        redThresholdSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                redThresholdLabel.setText(redThresholdSlider.getValue() + "");
            }
        });
        greenThresholdSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                greenThresholdLabel.setText(greenThresholdSlider.getValue() + "");
            }
        });
        blueThresholdSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                blueThresholdLabel.setText(blueThresholdSlider.getValue() + "");
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
                maximumButtonAction(image, thresholdSlider);
            }
        });
        redMaxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(checkImageType() && !imageIsGray) {
                    rgb[0].setRoi(image.getRoi());
                    maximumButtonAction(rgb[0], redThresholdSlider);
                }
            }
        });
        greenMaxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(checkImageType() && !imageIsGray) {
                    rgb[1].setRoi(image.getRoi());
                    maximumButtonAction(rgb[1], greenThresholdSlider);
                }
            }
        });
        blueMaxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(checkImageType() && !imageIsGray) {
                    rgb[2].setRoi(image.getRoi());
                    maximumButtonAction(rgb[2], blueThresholdSlider);
                }
            }
        });
        autoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                autoThresholdAction((String) autoThresholdComboBox.getSelectedItem(), image, thresholdSlider);
            }
        });

        redAutoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(checkImageType() && !imageIsGray)
                    autoThresholdAction((String) autoThresholdComboBox.getSelectedItem(), rgb[0], redThresholdSlider);
            }
        });
        greenAutoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(checkImageType() && !imageIsGray)
                    autoThresholdAction((String) autoThresholdComboBox.getSelectedItem(), rgb[1], greenThresholdSlider);
            }
        });
        blueAutoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(checkImageType() && !imageIsGray)
                    autoThresholdAction((String) autoThresholdComboBox.getSelectedItem(), rgb[2], blueThresholdSlider);
            }
        });

        darkPeaksCheck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                darkPeaks = darkPeaksCheck.isSelected();
                thresholdSlider.setInverted(darkPeaks);
                redThresholdSlider.setInverted(darkPeaks);
                greenThresholdSlider.setInverted(darkPeaks);
                blueThresholdSlider.setInverted(darkPeaks);
                maximumButton.setText(darkPeaks ? "min" : "max");
                redMaxButton.setText(darkPeaks ? "min" : "max");
                greenMaxButton.setText(darkPeaks ? "min" : "max");
                blueMaxButton.setText(darkPeaks ? "min" : "max");
            }
        });

        redCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeR = redCheckBox.isSelected();
                totalComboBox.setEnabled(takeR ? (takeG || takeB) : (takeG && takeB));
                redThresholdPanel.setVisible(takeR);
                defaultThresholdPanel.setVisible(!(takeR || takeG || takeB));
            }
        });

        greenCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeG = greenCheckBox.isSelected();
                totalComboBox.setEnabled(takeR ? (takeG || takeB) : (takeG && takeB));
                greenThresholdPanel.setVisible(takeG);
                defaultThresholdPanel.setVisible(!(takeR || takeG || takeB));
            }
        });

        blueCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeB = blueCheckBox.isSelected();
                totalComboBox.setEnabled(takeR ? (takeG || takeB) : (takeG && takeB));
                blueThresholdPanel.setVisible(takeB);
                defaultThresholdPanel.setVisible(!(takeR || takeG || takeB));
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
               runMultiPlot();
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
        quantification = Integer.parseInt(quantificationTextField.getText().replaceAll("[^\\d.]", ""));
    }

    private void getPlotValues() {
        if (!autoScaleCheckBox.isSelected()) yMax = Integer.parseInt(yAxisTextField.getText().replaceAll("[^\\d.]", "")); //make sure there are only digits
    }

    int getThreshold(String s) {
        switch (s) {
            case "red": return redThresholdSlider.getValue();
            case "green": return greenThresholdSlider.getValue();
            case "blue": return blueThresholdSlider.getValue();
            default: return thresholdSlider.getValue();
        }
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
        for (Component c : outerCountPanel.getComponents()) {
            c.setEnabled(countCellsCheckBox.isSelected());
        }
        for (Component c : innerCountPanel.getComponents()) {
            c.setEnabled(false);
        }
    }

    private void close() {
        WindowManager.removeWindow(this.frame);
        frame.dispose();
    }

    @Override
    public void imageOpened(ImagePlus imagePlus) {
        imgList.addItem(imagePlus.getTitle());
        imgList.setSelectedIndex(imgList.getItemCount() - 1);
        image = imagePlus;
    }

    @Override
    public void imageClosed(ImagePlus imagePlus) {
        if(WindowManager.getImageCount() > 0)
            imgList.removeItem(imagePlus.getTitle());
        else close();
    }

    @Override
    public void imageUpdated(ImagePlus imagePlus) {

    }
}