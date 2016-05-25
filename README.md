# android-report

This project allows to listen to Junit events (like test finished) and create a report (xml format) in the mobile device 
There is no need for ADB connection or using IDE to get the test result 

This project is a 'fork'  from http://zutubi.com/source/projects/android-junit-report/

I created this 'fork' as the above code didnt worked for me on android OS 4+


In order to build you need Gradle (Gradle version 2.10+)

1) run gradle build 

2) go to build/outputs/aar

3) unzip android-report-release.aar

4) copy classes.jar into your android application libs folder (you can rename the jar name)

5) change in AndroidManifest.xml in <instrumentation> tag android:name to com.github.junitlistener.lib.AndroiDWRunner.java

or in gradle set testInstrumentationRunner "com.github.junitlistener.lib.AndroiDWRunner"

6) now when you will run tests with this runner , the report file will be created in junitReport folder in the device 
