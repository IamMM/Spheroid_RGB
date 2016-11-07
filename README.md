Spheroid RGB Statistic Plugin for ImageJ
========================================

This Plugin is developed for ImageJ or Fiji.

Input:
------
RGB image of double or triple stained confocal image of an cancer spheroid.

---

Output:
-------
Spheroid RGB will take selected Regions of Interest and selected Color
Channels to run over each ROI the following statistics:

- Image:
    - counted cells marked with dots
    - All regions of interest (ROI) from ROI Manager
- Table (optional):
    - number of cells of of selected channel and selected ROI
    - threshold mean intensity of selected channel and selected ROI
    - mean intensity of the peaks which representing the nuclei
    - area of selected channel and selected ROI (number of pixels within threshold)
    - total area measurement of the ROI with calibration from source image
    - integrated density of selected channel and selected ROI (mean * area)
    - ratio values between channels (count, peak mean, mean, area)
    - ratio mean of each pixel value: mean of the ratio per pixel
    
    
Example output "Count an Mean" with the red and blue channel selected (major channel blue):

| Image   | ROI       | count (blue) | peaks mean (blue) | mean (blue) | area (blue) | integrated density (blue) | count (red) | peaks mean (red) | mean (red) | area (red) | integrated density (red) | total area (pixel) | total area (mm) | count ratio | peaks mean ratio | mean ratio | area fraction | ratio mean |
|---------|-----------|--------------|-------------------|-------------|-------------|---------------------------|-------------|------------------|------------|------------|--------------------------|--------------------|-----------------|-------------|------------------|------------|---------------|------------|
| EdU.tif | 0294-0255 | 403          | 128.94            | 48.396      | 98956       | 4789109                   | 193         | 130.969          | 27.152     | 67998      | 1846250                  | 98956              | 33018.352       | 0.479       | 1.016            | 2.706      | 0.687         | 0.42       |

- Multi Plot:
    - plot the average intensity of multiple profiles through the centroid of the spheroid (selected area)
    - maximum coordinates of average plot
    - bounds related to the gradient of the plot 
    - area under the plotted channel to major channel bounds
    
Example output "Multi Plot" with the red and blue channel selected (major channel blue):
    
| Plot             | red max x | red max y | red bounds x | red bounds y | blue max x | blue max y | blue bounds x | blue bounds y | red area (blue bounds) | blue area (blue bounds) |
|------------------|-----------|-----------|--------------|--------------|------------|------------|---------------|---------------|------------------------|-------------------------|
| Plot SN33267.tif | 245       | 206.76    | 536          | 9.468        | 496        | 207.161    | 641           | 7.918         | 67875.963              | 60766.86                |


---

Started from template [minimal Maven project](https://github.com/imagej/minimal-ij1-plugin/archive/master.zip) implementing an ImageJ 1.x plugin.

Cell counting based on the algorithm from the [ITCN Plugin](https://imagej.nih.gov/ij/plugins/itcn.html), which is looking for local intensity peaks in 8-bit images.
[Center for Bio-Image Informatics, University of California](http://bioimage.ucsb.edu/automatic-nuclei-counter-plug-in-for-imagej)

Spheroid RGB was developed for the [Auckland Cancer Society Research Center](https://auckland-northland.cancernz.org.nz/).