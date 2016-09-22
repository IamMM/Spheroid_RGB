Spheroid RGB Statistic Plugin for ImageJ
========================================

This Plugin is developed for ImageJ or Fiji.

Input:
------
RGB image of double or triple stained confocal image of an cancer spheroid.

***

Spheroid RGB will take selected Regions of Interest and selected Color Channels
to run ofer each ROI the following statistics:

Output:
-------
- Table:
    - number of cells of of selected channel and selected ROI
    - mean intensity of each selected color channel and selected ROI
    - ratio between number of cells in percent
- Image:
    - counted cells marked with dots
    - Regions of Interest (ROI) for check

---

Started from template minimal Maven project implementing an ImageJ 1.x plugin
--> https://github.com/imagej/minimal-ij1-plugin/archive/master.zip

For Cell counting the ITCN_Runner algorithm was used:
https://imagej.nih.gov/ij/plugins/itcn.html
http://bioimage.ucsb.edu/automatic-nuclei-counter-plug-in-for-imagej
