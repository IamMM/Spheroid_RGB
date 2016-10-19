import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
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

/**
 * Created on october 2016
 *
 * @author Maximlian Maske
 */
public class Spheroid_RGB implements PlugIn {
    // swing components
    private JFrame frame;
    private JComboBox imgList;
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
    private JButton plotAllButton;
    private JButton plotAverageButton;
    private JCheckBox showLines;
    private JSlider profileSlider;
    private JLabel profileTextField;
    private JRadioButton redRadioButton;
    private JRadioButton greenRadioButton;
    private JRadioButton blueRadioButton;
    private JCheckBox countCellsCheckBox;
    private JPanel countPanel;
    private JPanel innerCountPanel;
    private JButton averageButton;
    private JButton minimumButton;

    // constants
    private static final String TITLE = "Spheroid RGB";
    private static final String VERSION = " v0.4.0 ";
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
    static ImagePlus[] rgb;
    static boolean takeR;
    static boolean takeG;
    static boolean takeB;
    static int total;
    static boolean imageIsGray;

    // magic selection
    private double startTolerance = 128;
    private int startMode = 1;

    // multi plot
    private int plotChannel;

    public Spheroid_RGB() {
        initActionListeners();
        initImageList();
        initComponents();
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
        ImagePlus image = IJ.openImage("img/SN33267.tif");
//        ImagePlus image = IJ.openImage("img/SN33267.tif");
        image.show();

        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

    /**
     * @see ij.plugin.PlugIn#run(String)
     */
    @Override
    public void run(String arg) {
        if(RoiManager.getInstance() == null) {
            new RoiManager();
        }

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
        if(WindowManager.getCurrentImage() != null) {
            image = WindowManager.getCurrentImage();
        }else {
            IJ.showMessage("No images open");
            return;
        }

        getGuiValues();

        roiManager = RoiManager.getInstance();
        if (roiManager == null) roiManager = new RoiManager();

        if(!(takeR || takeG || takeB)) {
            IJ.showMessage("Nothing to do", "No Channel selected.");
            return;
        }
        if (roiManager.getCount() == 0) {
            IJ.showMessage("Nothing to do", "Roi Manager is empty.");
            return;
        }

        Table_Analyzer table_analyzer = new Table_Analyzer();
        if(countCellsCheckBox.isSelected()) {
            table_analyzer.runCountAndMean();
        }else {
            table_analyzer.runMean();
        }
    }

    private Multi_Plot runMultiPlot() {
        checkImageType();

        if (imageIsGray){
            return new Multi_Plot(image,profileSlider.getValue());
        } else {
            rgb = ChannelSplitter.split(image);
            setChannelLut();
            return new Multi_Plot(rgb[plotChannel], image, profileSlider.getValue());
        }
    }

    // check if Image is RGB
    private void checkImageType() {
        int type = image.getType();
        if (type == ImagePlus.GRAY8) {
            imageIsGray = true;
            PEAKS_COLOR = Color.RED;
        } else if (type == ImagePlus.GRAY16)
            IJ.showMessage("16-bit gray scale image not supported");
        else if (type == ImagePlus.GRAY32)
            IJ.showMessage("32-bit gray scale image not supported");
        else if (type == ImagePlus.COLOR_RGB) {
            imageIsGray = false;
            rgb = ChannelSplitter.split(image);
            setChannelLut();
        } else {
            IJ.showMessage("not supported");
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
            IJ.doWand(polygon.xpoints[0], polygon.ypoints[0], startTolerance, modes[startMode]);
            wandDialog.showDialog();
            if (wandDialog.wasOKed()) RoiManager.getInstance().addRoi(image.getRoi());
        }catch (Exception e){
            IJ.showMessage("Selection must be a point selection");
        }
    }

    // set suitable look up table for 8bit channel (pseudo color)
    private void setChannelLut() {
        rgb[0].setLut(LUT.createLutFromColor(Color.RED));
        rgb[1].setLut(LUT.createLutFromColor(Color.GREEN));
        rgb[2].setLut(LUT.createLutFromColor(Color.BLUE));
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
//        WindowManager.setCurrentWindow(WindowManager.getImage(name).getWindow());
        image.show();
        imgList.addItem(image.getTitle());
        imgList.setSelectedIndex(imgList.getItemCount() - 1);
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
            String clean = input.replaceAll("\\D", ""); // http://www.regular-expressions.info/shorthand.html
            minDistField.setText(Double.toString(Double.parseDouble(clean) / 2));
        }
    }

    private void maximumButtonAction() {
        Roi roi = image.getRoi();

        if (roi.isArea()) {
            ImageStatistics stats = roi.getImage().getStatistics();
            thresSlider.setValue((int) Math.ceil(stats.max));
        }
    }

    private void averageButtonAction() {
        Roi roi = image.getRoi();

        if (roi.isArea()) {
            ImageStatistics stats = roi.getImage().getStatistics();
            thresSlider.setValue((int) Math.ceil(stats.mean));
        }
    }

    private void minimumButtonAction() {
        Roi roi = image.getRoi();

        if (roi.isArea()) {
            ImageStatistics stats = roi.getImage().getStatistics();
            thresSlider.setValue((int) Math.ceil(stats.min));
        }
    }

    private void analyzeButtonActionPerformed() {
        checkImageType();
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
                image = WindowManager.getImage((String) imgList.getSelectedItem());

                if(image == null || imgList.getItemCount() != WindowManager.getImageCount()) {
                    initImageList();
                    image = WindowManager.getCurrentImage();
                }

                if(imgList.getItemCount() != 0) {
                    WindowManager.setCurrentWindow(WindowManager.getImage((String) imgList.getSelectedItem()).getWindow());
//                    WindowManager.toFront(WindowManager.getFrame(WindowManager.getCurrentImage().getTitle()));
                } else {
                    IJ.showMessage("No images open", "It seems like you closed all image windows.");
                }
            }
        });

        countCellsCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (Component c : countPanel.getComponents()) {
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
        averageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                averageButtonAction();
            }
        });

        minimumButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                minimumButtonAction();
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

                totalCheckBox.setEnabled(takeR && takeG && takeB);
            }
        });

        greenCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeG = greenCheckBox.isSelected();
                totalCheckBox.setEnabled(takeR && takeG && takeB);
            }
        });

        blueCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                takeB = blueCheckBox.isSelected();
                totalCheckBox.setEnabled(takeR && takeG && takeB);
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });

        //Multi plot
        redRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                plotChannel = 0; //red
            }
        });

        greenRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                plotChannel = 1; //green
            }
        });

        blueRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                plotChannel = 2; //blue
            }
        });

        profileSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                profileTextField.setText(profileSlider.getValue() + " lines");
            }
        });

        showLines.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                image.setHideOverlay(!showLines.isSelected());
            }
        });

        plotAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(image.getRoi() == null) {
                    IJ.showMessage("Nothing to do.", "No Roi selected");
                } else {
                    runMultiPlot().plotAll();
                    showLines.setEnabled(true);
                }
            }
        });

        plotAverageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(image.getRoi() == null) {
                    IJ.showMessage("Nothing to do.", "No Roi selected");
                } else {
                    runMultiPlot().plotAverage();
                    showLines.setEnabled(true);
                }
            }
        });
    }

    private void getGuiValues() {
        cellWidth = Integer.parseInt(cellWidthField.getText().replaceAll("\\D", "")); //make sure there are only digits
        minDist = Double.parseDouble(minDistField.getText()); //.replaceAll("\\D", "")
        threshold = thresSlider.getValue();
        doubleThreshold = 10 * ((double)threshold /255);
        total = totalCheckBox.getSelectedIndex();
    }

    private void setLookAndFeel(JFrame frame) {
        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(frame);
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

    public void setIcon() {
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
        totalCheckBox.addItem("Red");
        totalCheckBox.addItem("Green");
        totalCheckBox.addItem("Blue");

        // initialize radio button group
        ButtonGroup group = new ButtonGroup();
        group.add(redRadioButton);
        group.add(greenRadioButton);
        group.add(blueRadioButton);
    }
}