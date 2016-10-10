/**
 * Created on January 17, 2006, 10:11 AM
 *
 * @author Thomas Kuo
 */

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayList;

public class Nuclei_Counter {
    // Input parameters with default values
    private int width;                     // Filter width
    private double min_dist;               // Min distance
    private double threshold;              // Threshold
    private boolean darkPeaks;             // Select dark or light peaks
    private double sigma;                  // Standard Deviation
    private double variance;               // Variance
    private ImagePlus inputImage;            // ImagePlus for current image
    private ImageProcessor ip;             // ImageProcessor for current image
    private ImagePlus maskImp;             // ImagePlus for mask image (null if does not exist)
    private int maskID;                    // ID for mask image
    private static String maskName = null; // name for mask image

    private static final String strNONE = "Use selected ROI";

    double[] kernel;

    //output fields
    private ArrayList<Point> peaks;
    private int numberOfCells;
    private ImagePlus resultImage;


    public Nuclei_Counter(ImagePlus imp, int width, double min_dist, double threshold, boolean darkPeaks, ImagePlus maskImp) {
        this.inputImage = imp;
        this.ip = imp.getProcessor();

        this.maskImp = maskImp;

        this.width = width;
        this.min_dist = min_dist;
        this.threshold = threshold;
        this.darkPeaks = darkPeaks;

        sigma = ((double) width - 1.0) / 3.0;
        variance = sigma * sigma;

        maskID = 0;
        maskName = null;

//        start();
    }

    public void run() {
        //ImagePlus impl;
        boolean inputs;
        double image[][];

        // Set ROI
        if (maskImp != null) {
            ip.resetRoi();
        }
        Rectangle r = ip.getRoi();

        // 2 Compute kernel
        IJ.showStatus("Finding Kernel");
        kernel = findKernel();

        // 3 Convolution
        IJ.showStatus("Convolution");

        image = filter2(ip, kernel, width, width);

        for (int i = 0; i < ip.getWidth(); i++) {
            for (int j = 0; j < ip.getHeight(); j++) {
                if (image[i][j] < threshold) image[i][j] = threshold;
                image[i][j] -= threshold;
            }
        }

        // 4 Find Maximum
        IJ.showStatus("Finding Maximums");

        // Create Mask
        ImageProcessor ipMask = null;
        int border = 1;
        boolean[][] mask = new boolean[r.width][r.height];

        if (maskImp != null) {
            ImageProcessor ipMask2 = maskImp.getProcessor();
            ipMask = ipMask2.duplicate();
        } else {
            if (inputImage.getMask() != null) {
                ipMask = (inputImage.getMask()).duplicate();
            }
        }

        // Get area
        int numPixels = 0;
        if (maskImp == null) {
            // Process selected ROI
            for (int i = 0; i < r.width; i++) {
                for (int j = 0; j < r.height; j++) {
                    if (!(ipMask != null && ipMask.getPixelValue(i, j) == 0)) {
                        numPixels++;
                    }
                }
            }
        } else {
            for (int i = 0; i < r.width; i++) {
                for (int j = 0; j < r.height; j++) {
                    if (!(ipMask != null && ipMask.getPixelValue(i, j) == 0)) {
                        numPixels++;
                    }
                }
            }
        }

        // Get area if ROI selected.

        if (ipMask != null) {
            ipMask.dilate();
        }

        for (int i = 0; i < r.width; i++) {
            for (int j = 0; j < r.height; j++) {
                if ((ipMask != null && ipMask.getPixelValue(i, j) == 0) || i < border || i >= r.width - border || j < border || j >= r.height - border) {
                    mask[i][j] = false;
                } else {
                    mask[i][j] = true;
                }
            }
        }

        // Local Maximum

        peaks = find_local_max(image, r, Math.floor((double) width / 3.0), min_dist, mask);

        // 5 results
        numberOfCells = peaks.size();

        ImageProcessor ipResults = (ip.duplicate()).convertToRGB();

        //transform roi related coordinates to image based coordinates
        for (int i = 0; i < numberOfCells; i++) {
            Point pt = peaks.get(i);
            pt.x = pt.x + r.x;
            pt.y = pt.y + r.y;
            peaks.set(i,pt);
        }

//        resultImage = new ImagePlus("Results " + inputImage.getTitle(), ipResults);
//
//        ipResults.setColor(java.awt.Color.WHITE);
//        ipResults.setLineWidth(1);
//
//        Point pt;
//        for (int i = 0; i < numberOfCells; i++) {
//            pt = peaks.get(i);
//
//            ipResults.drawDot(pt.x + r.x, pt.y + r.y);
//
//            //IJ.write("Peak at: "+(pt.x+r.x)+" "+(pt.y+r.y)+" "+image[pt.x+r.x][pt.y+r.y]);
//        }
//
//        ipResults.setColor(java.awt.Color.yellow);
//        ipResults.drawOval(r.x, r.y, r.width, r.height);

//        IJ.write("Image: " + inputImage.getTitle());
//
//        // Read units
//        Calibration cali = inputImage.getCalibration();
//
//        DecimalFormat densityForm = new DecimalFormat("###0.00");
//
//        if (cali == null) {
//            IJ.write("Number of Cells: " + numberOfCells);
//        } else {
//            IJ.write("Number of Cells: " + numberOfCells + " in " + densityForm.format((double) numPixels * cali.pixelHeight * cali.pixelWidth) + " square " + cali.getUnits());
//            IJ.write("Density: " + densityForm.format((double) peaks.size() / ((double) numPixels * cali.pixelHeight * cali.pixelWidth)) + " cells per square " + cali.getUnit());
//        }
//
//        IJ.write(".........................................................................................");
//
//        resultImage.show();

        return;

    }

    private double[] findKernel() {
        double[] hg = new double[width * width];
        double[] h = new double[width * width];
        double hgSum = 0, hSum = 0;
        double kSum = 0, kProd = 1;
        double bounds = ((double) width - 1.0) / 2.0;
        int index;

        index = 0;
        for (double n1 = -bounds; n1 <= bounds; n1++) {
            for (double n2 = -bounds; n2 <= bounds; n2++) {
                hg[index] = Math.exp(-(n1 * n1 + n2 * n2) / (2 * variance));
                hgSum += hg[index];
                h[index] = (n1 * n1 + n2 * n2 - 2 * variance) * hg[index] / (variance * variance);  // v2 added
                hSum += h[index];
                index++;
            }
        }

        index = 0;
        for (double n1 = -bounds; n1 <= bounds; n1++) {
            for (double n2 = -bounds; n2 <= bounds; n2++) {
                h[index] = (h[index] - hSum / (double) (width * width)) / hgSum;
                index++;
            }
        }

        for (int i = 0; i < width * width; i++) {
            kSum += h[i];
        }

        double kOffset = kSum / (width * width);
        for (int i = 0; i < width * width; i++) {
            h[i] -= kOffset;
        }


        return h;
    }

    private double[][] filter2(double image[][], int width, int height, double[] kern, int kh, int kw) {
        double[][] dr = new double[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                dr[x][y] = 0;

                for (int i = 0; i < kw; i++) {
                    for (int j = 0; j < kh; j++) {

                        try {
                            if ((x + i - (kw - 1) / 2) >= 0 && (x + i - (kw - 1) / 2) < width &&
                                    (y + j - (kw - 1) / 2) >= 0 && (y + j - (kw - 1) / 2) < height) {
                                dr[x][y] += kern[i + kw * j] * image[(x + i - (kw - 1) / 2)][(y + j - (kw - 1) / 2)];
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            IJ.showMessage("Array out of Bounds: (" + x + ", " + y + ") (" + i + ", " + j + ") ");
                        }
                    }
                }

            }
        }

        return dr;
    }

    private double[][] filter2(ImageProcessor ip, double[] kern, int kh, int kw) {
        int imgW = ip.getWidth();
        int imgH = ip.getHeight();

        Rectangle r = ip.getRoi();

        byte[] pixels = (byte[]) ip.getPixels();
        double pix;
        double[][] dr = new double[imgW][imgH];

        for (int x = 0; x < imgW; x++) {
            for (int y = 0; y < imgH; y++) {
                dr[x][y] = 0;

                for (int i = 0; i < kw; i++) {
                    for (int j = 0; j < kh; j++) {

                        try {
                            if ((x + i - (kw - 1) / 2) >= 0 && (x + i - (kw - 1) / 2) < imgW &&
                                    (y + j - (kw - 1) / 2) >= 0 && (y + j - (kw - 1) / 2) < imgH) {

                                if (darkPeaks)
                                    pix = (double) (0xff & pixels[(x + i - (kw - 1) / 2) + imgW * (y + j - (kw - 1) / 2)]);
                                else
                                    pix = 255.0 - (double) (0xff & pixels[(x + i - (kw - 1) / 2) + imgW * (y + j - (kw - 1) / 2)]);

                                dr[x][y] += kern[i + kw * j] * pix;
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            IJ.showMessage("Array out of Bounds: (" + x + ", " + y + ") (" + i + ", " + j + ") ");
                        }
                    }
                }
                //IJ.write(dr[x][y]+" ");
            }
        }

        return dr;
    }

    private ArrayList<Point> find_local_max(double[][] image, Rectangle r, double epsilon, double min_dist, boolean[][] mask) {
        ArrayList ind_n = new ArrayList();
        ArrayList ind_n_ext = new ArrayList();

        // prepare neighborhood indices
        double n_dim = epsilon;

        for (double i = -n_dim; i <= n_dim; i++) {
            for (double j = -n_dim; j <= n_dim; j++) {
                if (i != 0 && j != 0 && ((i * i + j * j) <= epsilon * epsilon)) {
                    ind_n.add(new Point((int) i, (int) j));
                }
            }
        }
        //int N_n = ind_n.size();

        // prepare extended neighborhood indices
        n_dim = min_dist;

        for (int i = (int) (-n_dim); i <= n_dim; i++) {
            for (int j = (int) (-n_dim); j <= n_dim; j++) {
                if ((i * i + j * j) <= min_dist * min_dist) {
                    ind_n_ext.add(new Point((int) i, (int) j));
                }
            }
        }
        //int N_n_ext = ind_n_ext.size();

        ArrayList<Point> peaks = new ArrayList();


        double minimum = 0;
        while (true) {
            double maximum = minimum;
            int x = 0, y = 0;
            //int mx=0, my=0;

            for (int i = 0; i < r.width; i++) {
                for (int j = 0; j < r.height; j++) {
                    if ((image[i + r.x][j + r.y] > maximum) && mask[i][j]) {
                        maximum = image[i + r.x][j + r.y];
                        x = i;
                        y = j;
                    }
                }
            }

            if (maximum == minimum)
                break;

            // Verify it is a maximum
            boolean flag = true;
            for (int i = 0; i < ind_n.size(); i++) {

                if (!flag) break;

                Point ind_nPt = (Point) ind_n.get(i);
                int nx = x + ind_nPt.x;
                int ny = y + ind_nPt.y;

                try {
                    flag = flag && (maximum >= image[nx + r.x][ny + r.y]);
                } catch (ArrayIndexOutOfBoundsException e) {
                }
            }

            if (flag) {
                peaks.add(new Point(x, y));
            } else {
                mask[x][y] = false;
            }

            for (int i = 0; i < ind_n_ext.size(); i++) {
                Point ind_n_extPt = (Point) ind_n_ext.get(i);
                int nx = x + ind_n_extPt.x;
                int ny = y + ind_n_extPt.y;

                if (nx >= 0 && nx < r.width && ny >= 0 && ny < r.height) {
                    mask[nx][ny] = false;
                }
            }

        }

        return peaks;
    }

    public ImagePlus getResultImage() {
        return resultImage;
    }

    public int getNumberOfCells() {
        return numberOfCells;
    }

    public ArrayList<Point> getPeaks() {
        return peaks;
    }
}