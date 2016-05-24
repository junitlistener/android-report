package com.github.junitlistener.lib;

import android.content.Context;
import android.support.test.internal.runner.listener.InstrumentationRunListener;
import android.util.Log;
import android.util.Xml;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.xmlpull.v1.XmlSerializer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

/**
 * Created by dw on 3/11/16.
 * Fork of http://zutubi.com/source/projects/android-junit-report/
 */
public class JUnitReportListener extends InstrumentationRunListener {
    private static final String LOG_TAG = "JUnitReportListener";

        private static final String ENCODING_UTF_8 = "utf-8";
        public static final String TOKEN_SUITE = "__suite__";
        private static final String TAG_SUITES = "testsuites";
        private static final String TAG_SUITE = "testsuite";
        private static final String TAG_CASE = "testcase";
        private static final String TAG_FAILURE = "failure";
        private static final String ATTRIBUTE_NAME = "name";
        private static final String ATTRIBUTE_CLASS = "classname";
        private static final String ATTRIBUTE_TYPE = "type";
        private static final String ATTRIBUTE_MESSAGE = "message";
        private static final String ATTRIBUTE_TIME = "time";

        private static final String[] DEFAULT_TRACE_FILTERS = new String[] {
                "junit.framework.TestCase", "junit.framework.TestResult",
                "junit.framework.TestSuite",
                "junit.framework.Assert.", // don't filter AssertionFailure
                "java.lang.reflect.Method.invoke(", "sun.reflect.",
                // JUnit 4 support:
                "org.junit.", "junit.framework.JUnit4TestAdapter", " more",
                // Added for Android
                "android.test.", "android.app.Instrumentation",
                "java.lang.reflect.Method.invokeNative",
        };
        private Context mTargetContext;
        private String mReportFile;
        private String mReportDir;
        private boolean mFilterTraces;
        private boolean mMultiFile;
        private FileOutputStream mOutputStream;
        private XmlSerializer mSerializer;
        private String mCurrentSuite;
        private boolean mTimeAlreadyWritten = false;
        private long mTestStartTime;


        public JUnitReportListener( Context targetContext,
                                    String reportFile,
                                    String reportDir,
                                    boolean filterTraces,
                                    boolean multiFile)
        {
            Log.i(LOG_TAG, "Listener created with arguments:\n  report file  : '" + reportFile + "'\n" + "  report dir   : '" + reportDir + "'\n" + "  filter traces: " + filterTraces + "\n" + "  multi file   : " + multiFile);

            this.mTargetContext = targetContext;
            this.mReportFile = reportFile;
            this.mReportDir = reportDir;
            this.mFilterTraces = filterTraces;
            this.mMultiFile = multiFile;
        }


    @Override
    public void testStarted(Description description) throws Exception {
        if(description.isTest()) {
            checkForNewSuite(description);
            this.mSerializer.startTag("", TAG_CASE);
            this.mSerializer.attribute("", ATTRIBUTE_CLASS, description.getClassName());
            this.mSerializer.attribute("", ATTRIBUTE_NAME, description.getDisplayName());

            this.mTimeAlreadyWritten = false;
            this.mTestStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void testFinished(Description description) throws Exception {

        recordTestTime();
        this.mSerializer.endTag("", TAG_CASE);
        this.mSerializer.flush();
    }



    private void checkForNewSuite(Description description) throws IOException {
        String suiteName = description.getClassName();
        if (mCurrentSuite == null || !mCurrentSuite.equals(suiteName)) {
            if (mCurrentSuite != null) {
                if (mMultiFile) {
                    close();
                } else {
                    mSerializer.endTag("", TAG_SUITE);
                    mSerializer.flush();
                }
            }

            openIfRequired(suiteName);
            mSerializer.startTag("", TAG_SUITE);
            mSerializer.attribute("", ATTRIBUTE_NAME, suiteName);
            mCurrentSuite = suiteName;
        }
    }







    @Override
    public void testFailure(Failure failure) throws Exception{
        addProblem(TAG_FAILURE, failure);
    }


    private void addProblem(String tag, Failure failure) {
        try {
            recordTestTime();
            mSerializer.startTag("", tag);
            mSerializer.attribute("", ATTRIBUTE_MESSAGE, safeMessage(failure));
            mSerializer.attribute("", ATTRIBUTE_TYPE, failure.getClass().getName());
            StringWriter w = new StringWriter();
            failure.getException().printStackTrace(mFilterTraces ? new FilteringWriter(w) : new PrintWriter(w));
            mSerializer.text(w.toString());
            mSerializer.endTag("", tag);
            mSerializer.flush();
        } catch (Exception e) {
            Log.e(LOG_TAG, safeMessage(e));
        }
    }


    private void openIfRequired(String suiteName) {
        try {
            if (mSerializer == null) {
                mOutputStream = openOutputStream(resolveFileName(suiteName));
                mSerializer = Xml.newSerializer();
                mSerializer.setOutput(mOutputStream, ENCODING_UTF_8);
                mSerializer.startDocument(ENCODING_UTF_8, true);
                if (!mMultiFile) {
                    mSerializer.startTag("", TAG_SUITES);
                }
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, safeMessage(e));
            throw new RuntimeException("Unable to open serializer: " + e.getMessage(), e);
        }
    }


    private String resolveFileName(String suiteName) {
        String fileName = mReportFile;
        if (mMultiFile) {
            fileName = fileName.replace(TOKEN_SUITE, suiteName);
        }
        return fileName;
    }



    private FileOutputStream openOutputStream(String fileName) throws IOException {
        if (mReportDir == null) {
            Log.d(LOG_TAG, "No reportDir specified. Opening report file '" + fileName + "' in internal storage of app under test");
            return mTargetContext.openFileOutput(fileName, Context.MODE_WORLD_READABLE);
        } else {


            ensureDirectoryExists(mReportDir);

            File outputFile = new File(mReportDir, fileName);
            Log.d(LOG_TAG, "Opening report file '" + outputFile.getAbsolutePath() + "'");
            return new FileOutputStream(outputFile);
        }
    }



    private void ensureDirectoryExists(String path) throws IOException
    {
        File dir = new File(path);
        if ((!dir.isDirectory()) && (!dir.mkdirs())) {
            String message = "Cannot create directory '" + path + "'";
            Log.e(LOG_TAG, message);
            throw new IOException(message);
        }
    }





    private void recordTestTime() throws IOException {
        if (!this.mTimeAlreadyWritten) {
            this.mTimeAlreadyWritten = true;
            this.mSerializer.attribute("", ATTRIBUTE_TIME, String.format(Locale.ENGLISH, "%.3f", new Object[]{Double.valueOf((System.currentTimeMillis() - this.mTestStartTime) / 1000.0D)}));
        }
    }

    public void close() {
        if (mSerializer != null) {
            try {
                // Do this just in case endTest() was not called due to a crash in native code.
                if (TAG_CASE.equals(mSerializer.getName())) {
                    mSerializer.endTag("", TAG_CASE);
                }

                if (mCurrentSuite != null) {
                    mSerializer.endTag("", TAG_SUITE);
                }

                if (!mMultiFile) {
                    mSerializer.endTag("", TAG_SUITES);
                }
                mSerializer.endDocument();
                mSerializer.flush();
                mSerializer = null;
            } catch (IOException e) {
                Log.e(LOG_TAG, safeMessage(e));
            }
        }

        if (mOutputStream != null) {
            try {
                mOutputStream.close();
                mOutputStream = null;
            } catch (IOException e) {
                Log.e(LOG_TAG, safeMessage(e));
            }
        }
    }



    private String safeMessage(Failure failure)
    {
        String message = failure.getMessage();
        return failure.getClass().getName() + ": " + (message == null ? "<null>" : message);
    }
    private String safeMessage(Throwable error)
    {
        String message = error.getMessage();
        return error.getClass().getName() + ": " + (message == null ? "<null>" : message);
    }


    private static class FilteringWriter extends PrintWriter
    {
        public FilteringWriter(Writer out)
        {
            super(out);
        }

        public void println(String s)
        {
            for (String filtered : JUnitReportListener.DEFAULT_TRACE_FILTERS) {
                if (s.contains(filtered)) {
                    return;
                }
            }

            super.println(s);
        }
    }
}
