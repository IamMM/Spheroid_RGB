import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayList;

/**
 * Created on January 17, 2006, 10:11 AM
 * Edited by Maximilian M. on 27 October, 2016
 * @author Thomas Kuo, Maximilian Maske
 *
 */
class Nuclei_Counter {
    // Input parameters with default values
    private int width;                     // Filter width
    private double min_dist;               // Min distance
    private double threshold;              // Threshold
    private boolean darkPeaks;             // Select dark or light peaks
    private ImageProcessor ip;             // ImageProcessor for current image
    private ImageProcessor ipMask;

    //output fields
    private ArrayList<Point> peaks;

    Nuclei_Counter(ImagePlus imp, int width, double min_dist, double threshold, boolean darkPeaks) {
        Roi roi = imp.getRoi();
        int roiPosition = roi.getPosition();
        if(imp.getStackSize() > 1 && roiPosition > 1) {
            ip = imp.getStack().getProcessor(roiPosition);
        } else ip = imp.getProcessor();
        ip.setRoi(roi);
        if(imp.getMask() != null ) ipMask = imp.getMask().duplicate();

        this.width = width;
        this.min_dist = min_dist;
        this.threshold = threshold;
        this.darkPeaks = darkPeaks;
    }

    void run() {
        double image[][];

        // Set ROI
        Rectangle r = ip.getRoi();

        // Compute kernel
        double[] kernel;
        IJ.showStatus("Finding Kernel");
        kernel = findKernel();

        // Convolution
        IJ.showStatus("Convolution");

        image = filter2(ip, kernel, width, width);

        for (int i = 0; i < ip.getWidth(); i++) {
            for (int j = 0; j < ip.getHeight(); j++) {
                if (image[i][j] < threshold) image[i][j] = 0; // used to be: '= threshold;'
//                image[i][j] -= threshold;
            }
        }

        // Find Maximum
        IJ.showStatus("Finding Maximums");

        // Get area if ROI selected.
        if (ipMask != null) {
            ipMask.dilate();
        }

        // Create Mask
        int border = 1;
        boolean[][] mask = new boolean[r.width][r.height];

        for (int i = 0; i < r.width; i++) {
            for (int j = 0; j < r.height; j++) {
                mask[i][j] = !((ipMask != null && ipMask.getPixelValue(i, j) == 0) || i < border || i >= r.width - border || j < border || j >= r.height - border);
            }
        }

        // Local Maximum
        peaks = find_local_max(image, r, Math.floor((double) width / 3.0), min_dist, mask);

        // transform roi related coordinates to image based coordinates
        int numberOfCells = peaks.size();
        for (int i = 0; i < numberOfCells; i++) {
            Point pt = peaks.get(i);
            pt.x = pt.x + r.x;
            pt.y = pt.y + r.y;
            peaks.set(i,pt);
        }
    }

    private double[] findKernel() {
        double[] hg = new double[width * width];
        double[] h = new double[width * width];
        double hgSum = 0, hSum = 0;
        double kSum = 0;
        double bounds = ((double) width - 1.0) / 2.0;
        int index;

        index = 0;
        double sigma = ((double) width - 1.0) / 3.0;
        double variance = sigma * sigma;
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

    private double[][] filter2(ImageProcessor ip, double[] kern, int kh, int kw) {
        int imgW = ip.getWidth();
        int imgH = ip.getHeight();

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
            }
        }

        return dr;
    }

    private ArrayList<Point> find_local_max(double[][] image, Rectangle r, double epsilon, double min_dist, boolean[][] mask) {
        ArrayList<Point> ind_n = new ArrayList<>();
        ArrayList<Point> ind_n_ext = new ArrayList<>();

        // prepare neighborhood indices
        double n_dim = epsilon;

        for (double i = -n_dim; i <= n_dim; i++) {
            for (double j = -n_dim; j <= n_dim; j++) {
                if (i != 0 && j != 0 && ((i * i + j * j) <= epsilon * epsilon)) {
                    ind_n.add(new Point((int) i, (int) j));
                }
            }
        }

        // prepare extended neighborhood indices
        n_dim = min_dist;

        for (int i = (int) (-n_dim); i <= n_dim; i++) {
            for (int j = (int) (-n_dim); j <= n_dim; j++) {
                if ((i * i + j * j) <= min_dist * min_dist) {
                    ind_n_ext.add(new Point(i, j));
                }
            }
        }

        ArrayList<Point> peaks = new ArrayList<>();

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
            for (Point ind_nPt : ind_n) {

                if (!flag) break;

                int nx = x + ind_nPt.x;
                int ny = y + ind_nPt.y;

                flag = (maximum >= image[nx + r.x][ny + r.y]);

            }

            if (flag) {
                peaks.add(new Point(x, y));
            } else {
                mask[x][y] = false;
            }

            for (Point ind_n_extPt : ind_n_ext) {
                int nx = x + ind_n_extPt.x;
                int ny = y + ind_n_extPt.y;

                if (nx >= 0 && nx < r.width && ny >= 0 && ny < r.height) {
                    mask[nx][ny] = false;
                }
            }

        }

        return peaks;
    }

    ArrayList<Point> getPeaks() {
        return peaks;
    }
}