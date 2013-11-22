zoodirector
===========

A simple java ui for viewing and modifying zookeeper cluster nodes.

The UI is composed of 3 main view. The first is for viewing/editing the node tree, the second for viewing/editing the selected node data and information, and the last for managing node data watches.

The node tree is automatically synced at all times to the zookeeper cluster based on detailed watches. To have a little fun, see how responsive it is when you run some automated node creation and deletion.

Configuration
-------------

The zoodirector configuration file by default is located at ```${home}/zoodirector.xml```. The path of this configuration file can be set via the ```ZOODIRECTOR_CONFIG``` environment variable.

All zoodirector settings are saved to this file. It will be created the first time any default settings have been modified through the settings editor.

Build
-----
zoodirector is built via maven and is configured to generate an executable jar which includes all required dependencies.

	mvn clean package

Running
-------
Jump into the target directory and execute the following command to launch zoodirector.

	java -jar zoodirector-0.0.1-SNAPSHOT-jar-with-dependencies.jar