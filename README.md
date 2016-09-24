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
- Table:
    - number of cells of of selected channel and selected ROI
    - mean intensity of each selected color channel and selected ROI
    - ratio between number of cells in percent
- Image:
    - counted cells marked with dots
    - Regions of Interest (ROI) for check

---

Started from template minimal Maven project implementing an ImageJ 1.x plugin:
https://github.com/imagej/minimal-ij1-plugin/archive/master.zip

Cell counting based on the algorithm from the ITCN Plugin, which is looking for local intensity peaks in 8-bit images: \s\s
https://imagej.nih.gov/ij/plugins/itcn.html
http://bioimage.ucsb.edu/automatic-nuclei-counter-plug-in-for-imagej
