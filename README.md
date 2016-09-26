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
    - mean intensity of each selected color channel and selected ROI
    - ratio between number of cells in percent

ROI | number of red cells | mean Value red | number of blue cells | mean value blue | count ratio (%) | intensity ratio (%)
--- | --- | --- | --- | --- | --- | --- 
1 | 186	| 21.036 | 395 | 52.744| 47.089 | 39.882
2 | 165	| 19.769 | 363 | 40.075 | 45.455 | 49.331
3 | 26 | 15.426 | 40| 24.944 | 65.000 | 61.840
4 | 125| 12.391 | 219 | 25.525 | 57.078 | 48.544


---

Started from template [minimal Maven project](https://github.com/imagej/minimal-ij1-plugin/archive/master.zip) implementing an ImageJ 1.x plugin.


Cell counting based on the algorithm from the [ITCN Plugin](https://imagej.nih.gov/ij/plugins/itcn.html), which is looking for local intensity peaks in 8-bit images.
[Center for Bio-Image Informatics, University of California](http://bioimage.ucsb.edu/automatic-nuclei-counter-plug-in-for-imagej)

Spheroid RGB was developed for the Auckland Cancer Society Research Center.