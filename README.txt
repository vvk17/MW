Java Workload Scheduler Web Starter

This application demonstrates how to use the Workload Scheduler service, with the 'Liberty for Java™' runtime on IBM Cloud.


Files

The Java Workload Scheduler Web Starter application contains the following contents:

*   javaCloudantWorkloadSchedulerApp.war

    This WAR file is actually the application itself. It is the only file that'll be pushed to and run on the Bluemix cloud. Every time your application code is updated, you'll need to regenerate this WAR file and push to Bluemix again. See the next section on detailed steps.

*   WebContent/

    This directory contains the client side code (HTML/CSS/JavaScript) of your application as well as compiled server side java classes and necessary JAR libraries. The content inside this directory is all you need to generate the final WAR file.
    
*   src/

    This directory contains the server side code (JAVA) of your application.
    
*   build.xml

    This file allows you to easily build your application using Ant.
    
*	dep-jar/

	This directory contains libraries that are needed to compile locally, but are provided by Bluemix and not included in the WAR file. 
	
