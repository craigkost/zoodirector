zoodirector
===========

A simple java ui for viewing and modifying zookeeper cluster nodes.

The UI is composed of two main panels. The first is for viewing/editing the node tree, the second for viewing/editing the selected node data and information.

Configuration
-------------

The zoodirector configuration file by default is located at ```${home}/.zoodirector```. The path of this configuration file can be set via the ```ZOODIRECTOR_CONFIG``` environment variable.

Connection strings, retry timeout, and application window size settings can be configured via this file.

### Example
	window.width=800
	window.height=600
	connection.retryPeriod=1000
	connection=127.0.0.1
	connection=localhost:2181

Build
-----
zoodirector is build via maven and is configured to generate an executable jar.

	mvn clean package

Running
-------
Jump into the target directory and execute the following command to launch zoodirector.

	java -jar zoodirector-0.0.1-SNAPSHOT-jar-with-dependencies.jar