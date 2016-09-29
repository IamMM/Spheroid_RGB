import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.frame.*;
import java.awt.event.*;
import java.util.*;
import java.lang.*;

/*
 * NewFrame.java
 *
 * Created on January 17, 2006, 10:11 AM
 */

/**
 *
 * @author  Thomas Kuo
 */
public class Gui extends PlugInFrame {

    // Variables declaration - do not modify//GEN-BEGIN:variables
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
    private static int widthDefault = 10;
    private static double min_distDefault = 5.0;
    private static double thresDefault = 0.0;
    private static double thresPrecision = 10;

    public Gui() {
        super("Spheroid RGB");

        initComponents();
    }

    private void initComponents() {
        imagePanel = new java.awt.Panel();
        ccImageLabel = new java.awt.Label();
        filenameLabel = new java.awt.Label();
        varsPanel = new java.awt.Panel();
        widthLabel = new java.awt.Label();
        widthTextField = new java.awt.TextField();
        minDistUnitsLabel = new java.awt.Label();
        minDistLabel = new java.awt.Label();
        minDistTextField = new java.awt.TextField();
        widthUnitsLabel = new java.awt.Label();
        recomendLabel = new java.awt.Label();
        darkPeaksCheckbox = new java.awt.Checkbox();
        maskPanel = new java.awt.Panel();
        maskLabel = new java.awt.Label();
        maskChoice = new java.awt.Choice();
        openMaskButton = new java.awt.Button();
        buttonPanel = new java.awt.Panel();
        okButton = new java.awt.Button();
        exitButton = new java.awt.Button();
        widthButton = new java.awt.Button();
        minDistButton = new java.awt.Button();
        thresLabel = new java.awt.Label();
        thresTextField = new java.awt.TextField();
        thresScroll = new java.awt.Scrollbar();
        midPanel = new java.awt.Panel();

        java.awt.GridBagConstraints gridBagConstraints;
        int ipadx = 50;

        setLayout(new java.awt.GridLayout(5, 1));

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                exitForm(evt);
            }
        });


        // window manager stuff.....

        currImp = WindowManager.getCurrentImage();
        if (currImp == null) {
            IJ.beep();
            IJ.showStatus("No image");
            return;
        }
        if (!currImp.lock())
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

        imagePanel.setLayout(new java.awt.GridLayout());

        ccImageLabel.setAlignment(java.awt.Label.RIGHT);
        ccImageLabel.setText("Image Name:");
        imagePanel.add(ccImageLabel);

        filenameLabel.setText(currImp.getTitle());
        imagePanel.add(filenameLabel);

        add(imagePanel);

        //varsPanel.setLayout(new java.awt.GridLayout(3, 4));
        varsPanel.setLayout(new java.awt.GridBagLayout());

        widthLabel.setAlignment(java.awt.Label.RIGHT);
        widthLabel.setText("Width");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        varsPanel.add(widthLabel, gridBagConstraints);

//        widthLabel.setAlignment(java.awt.Label.RIGHT);
//        widthLabel.setText("Width");
//        varsPanel.add(widthLabel);

        widthTextField.setText(Integer.toString(widthDefault));
        widthTextField.addTextListener(new java.awt.event.TextListener() {
            public void textValueChanged(java.awt.event.TextEvent evt) {
                widthTextFieldTextValueChanged(evt);
            }
        });
        //varsPanel.add(widthTextField);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = ipadx;
        varsPanel.add(widthTextField, gridBagConstraints);

        minDistUnitsLabel.setText("pixels");
        varsPanel.add(minDistUnitsLabel);

        widthButton.setLabel("Measure Line Length");
        widthButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                widthButtonActionPerformed(evt);
            }
        });
        //varsPanel.add(widthButton);
        gridBagConstraints = new java.awt.GridBagConstraints();
        varsPanel.add(widthButton, gridBagConstraints);

        minDistLabel.setAlignment(java.awt.Label.RIGHT);
        minDistLabel.setText("Minimum Distance");
        varsPanel.add(minDistLabel);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        varsPanel.add(minDistLabel, gridBagConstraints);

        minDistTextField.setText(Double.toString(min_distDefault));
        varsPanel.add(minDistTextField);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = ipadx;
        varsPanel.add(minDistTextField, gridBagConstraints);

        widthUnitsLabel.setText("pixels");
        varsPanel.add(widthUnitsLabel);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        varsPanel.add(widthUnitsLabel, gridBagConstraints);

        minDistButton.setLabel("Measure Line Length");
        minDistButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                minDistButtonActionPerformed(evt);
            }
        });
        varsPanel.add(minDistButton);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        varsPanel.add(minDistButton, gridBagConstraints);

        thresLabel.setAlignment(java.awt.Label.RIGHT);
        thresLabel.setText("Threshold");
        varsPanel.add(thresLabel);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        varsPanel.add(thresLabel, gridBagConstraints);

        thresTextField.setText(Double.toString(thresDefault));
        thresTextField.addTextListener(new java.awt.event.TextListener() {
            public void textValueChanged(java.awt.event.TextEvent evt) {
                thresTextFieldTextValueChanged(evt);
            }
        });
        varsPanel.add(thresTextField);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = ipadx;
        varsPanel.add(thresTextField, gridBagConstraints);

        //varsPanel.add(thresScroll);
        thresScroll.setValues((int) (thresPrecision * thresDefault), 1, 0, 10 * (int) thresPrecision + 1);
        thresScroll.setOrientation(java.awt.Scrollbar.HORIZONTAL);
        thresScroll.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                thresScrollAdjustmentValueChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        varsPanel.add(thresScroll, gridBagConstraints);

        add(varsPanel);

        midPanel.setLayout(new java.awt.GridLayout(2, 1));

        recomendLabel.setAlignment(java.awt.Label.CENTER);
        recomendLabel.setText("(Recommended: Minimum Distance = Width/2)");
        midPanel.add(recomendLabel);

        darkPeaksCheckbox.setLabel("Detect Dark Peaks");
        midPanel.add(darkPeaksCheckbox);

        add(midPanel);

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
        openMaskButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMaskButtonActionPerformed(evt);
            }
        });

        maskPanel.add(openMaskButton);

        add(maskPanel);

        buttonPanel.setLayout(new java.awt.GridLayout());

        okButton.setLabel("Count");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(okButton);

        exitButton.setLabel("Exit");
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(exitButton);

        add(buttonPanel);

        pack();
        setSize(400, 375);
        GUI.center(this);
        show();
    }

    private void widthTextFieldTextValueChanged(TextEvent evt) {
        if(widthTextField.getText().isEmpty()) minDistTextField.setText("0.0");
        else minDistTextField.setText(Double.toString(Double.parseDouble(widthTextField.getText()) / 2));
    }

    private void widthButtonActionPerformed(ActionEvent evt) {
        Roi roi = currImp.getRoi();

        if (roi.isLine()) {
            Line line = (Line) roi;
            widthTextField.setText(Integer.toString((int) Math.ceil(line.getRawLength())));
        }
    }

    private void minDistButtonActionPerformed(ActionEvent evt) {
        Roi roi = currImp.getRoi();

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
        close();
    }

    private void okButtonActionPerformed(ActionEvent evt) {
        //close();

        widthDefault = Integer.parseInt(widthTextField.getText());
        min_distDefault = Double.parseDouble(minDistTextField.getText());
        thresDefault = Double.parseDouble(thresTextField.getText());

        int maskIndex = maskChoice.getSelectedIndex();
        String maskString = maskChoice.getSelectedItem();

        Integer maskID = (Integer) winIDList.get(maskIndex);

        ImagePlus maskImp = WindowManager.getImage(maskID.intValue());

        new ITCN_Runner(currImp, Integer.parseInt(widthTextField.getText()),
                Double.parseDouble(minDistTextField.getText()), Double.parseDouble(thresTextField.getText()),
                darkPeaksCheckbox.getState(), maskImp);
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

    /**
     * Exit the Application
     */
    private void exitForm(java.awt.event.WindowEvent evt) {
        currImp.unlock();
    }
}