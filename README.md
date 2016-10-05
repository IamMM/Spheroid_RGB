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
    - Regions of Interest (ROI) for check
- Table:
    - number of cells of of selected channel and selected ROI
    - threshold mean intensity of each selected color channel and selected ROI
    - mean intensity of the peaks which representing the nuclei
    - area measurement of the ROI with calibration from source image
    - ratio between number of cells  and mean values in percent

| ROI | count (red) | mean (red) | mean peak (red) | count (blue) | mean (blue) | mean peak (blue) | area (μm)  | count ratio (%) | mean ratio (%) | mean peak ratio (%) |
|-----|-------------|------------|-----------------|--------------|-------------|------------------|------------|-----------------|----------------|---------------------|
| 1   | 187         | 30.382     | 130.786         | 397          | 62.487      | 129.131          | 7.291      | 47.103          | 48.622         | 98.734              |
| 2   | 165         | 30.772     | 139             | 360          | 49.869      | 105.631          | 6.35       | 45.833          | 61.707         | 75.993              |
| 3   | 121         | 30.319     | 101.421         | 227          | 38.442      | 86.009           | 4.626      | 53.304          | 78.87          | 84.803              |
| 4   | 26          | 46.739     | 165.269         | 39           | 49.116      | 121.59           | 0.816      | 66.667          | 95.159         | 73.571              |


---

Started from template [minimal Maven project](https://github.com/imagej/minimal-ij1-plugin/archive/master.zip) implementing an ImageJ 1.x plugin.


Cell counting based on the algorithm from the [ITCN Plugin](https://imagej.nih.gov/ij/plugins/itcn.html), which is looking for local intensity peaks in 8-bit images.
[Center for Bio-Image Informatics, University of California](http://bioimage.ucsb.edu/automatic-nuclei-counter-plug-in-for-imagej)

Spheroid RGB was developed for the Auckland Cancer Society Research Center.