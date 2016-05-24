package com.github.junitlistener.lib;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.test.internal.runner.RunnerArgs;
import android.support.test.internal.runner.TestExecutor;
import android.support.test.internal.runner.TestRequest;
import android.support.test.internal.runner.TestRequestBuilder;
import android.support.test.internal.runner.listener.ActivityFinisherRunListener;
import android.support.test.internal.runner.listener.CoverageListener;
import android.support.test.internal.runner.listener.DelayInjector;
import android.support.test.internal.runner.listener.InstrumentationResultPrinter;
import android.support.test.internal.runner.listener.LogRunListener;
import android.support.test.internal.runner.listener.SuiteAssignmentPrinter;
import android.support.test.internal.runner.tracker.AnalyticsBasedUsageTracker;
import android.support.test.internal.runner.tracker.UsageTracker;
import android.support.test.internal.runner.tracker.UsageTrackerRegistry;
import android.support.test.runner.MonitoringInstrumentation;
import android.support.test.runner.lifecycle.ApplicationLifecycleCallback;
import android.support.test.runner.lifecycle.ApplicationLifecycleMonitorRegistry;
import android.util.Log;

import org.junit.runner.notification.RunListener;

/**
 * Created by dw on 3/12/16.
 * Fork of http://zutubi.com/source/projects/android-junit-report/
 */
public class AndroiDWRunner extends MonitoringInstrumentation {

    private static final String LOG_TAG = "AndroiDWRunner";

    private Bundle mArguments;
    private InstrumentationResultPrinter mInstrumentationResultPrinter = null;
    private RunnerArgs mRunnerArgs;
    private JUnitReportListener mListener;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;
        mRunnerArgs = new RunnerArgs.Builder().fromManifest(this).fromBundle(getArguments()).build();
        for (ApplicationLifecycleCallback listener : mRunnerArgs.appListeners) {
            ApplicationLifecycleMonitorRegistry.getInstance().addLifecycleCallback(listener);
        }
        start();
    }


    private Bundle getArguments() {
        return mArguments;
    }

    InstrumentationResultPrinter getInstrumentationResultPrinter() {
        return mInstrumentationResultPrinter;
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle results = new Bundle();
        try {
            TestExecutor.Builder executorBuilder = new TestExecutor.Builder(this);
            if (mRunnerArgs.debug) {
                executorBuilder.setWaitForDebugger(true);
            }

            addListeners(mRunnerArgs, executorBuilder);

            TestRequest testRequest = buildRequest(mRunnerArgs, getArguments());

            results = executorBuilder.build().execute(testRequest);

        } catch (RuntimeException e) {
            final String msg = "Fatal exception when running tests";
            Log.e(LOG_TAG, msg, e);
            results.putString(Instrumentation.REPORT_KEY_STREAMRESULT, msg + "\n" + Log.getStackTraceString(e));
        }
        finish(Activity.RESULT_OK, results);
    }


    // This method send broadcast message to another application on the device , I use it to know when test finish for the target package
    private void send(){
        Intent intent = new Intent();
        intent.setAction("com.tula.test.finished");
        intent.putExtra("package", mArguments.getString("package"));
        getContext().sendBroadcast(intent);
    }


    @Override
    public void finish(int resultCode, Bundle results) {
        try {
            if (mListener != null) {
                mListener.close();
            }
            UsageTrackerRegistry.getInstance().trackUsage("AndroiDWRunner");
            UsageTrackerRegistry.getInstance().sendUsages();
        } catch (RuntimeException re) {
            Log.w(LOG_TAG, "Failed to send analytics.", re);
        }
        send();
        super.finish(resultCode, results);
    }

    private void addListeners(RunnerArgs args, TestExecutor.Builder builder) {
        if (args.suiteAssignment) {
            builder.addRunListener(new SuiteAssignmentPrinter());
        } else {
            builder.addRunListener(new LogRunListener());
            mInstrumentationResultPrinter = new InstrumentationResultPrinter();
            builder.addRunListener(mInstrumentationResultPrinter);
            builder.addRunListener(new ActivityFinisherRunListener(this, new MonitoringInstrumentation.ActivityFinisher()));

            // I used external folder to save the results
            this.mListener = new JUnitReportListener(getTargetContext(),
                    "junit.xml",
                    Environment.getExternalStorageDirectory().getAbsolutePath() + "/junitReport/",
                    false,
                    true);

            builder.addRunListener(this.mListener);
            addDelayListener(args, builder);
            addCoverageListener(args, builder);
        }

        addListenersFromArg(args, builder);
    }

    private void addCoverageListener(RunnerArgs args, TestExecutor.Builder builder) {
        if (args.codeCoverage) {
            builder.addRunListener(new CoverageListener(args.codeCoveragePath));
        }
    }

    /**
     * Sets up listener to inject a delay between each test, if specified.
     */
    private void addDelayListener(RunnerArgs args, TestExecutor.Builder builder) {
        if (args.delayInMillis > 0) {
            builder.addRunListener(new DelayInjector(args.delayInMillis));
        } else if (args.logOnly && Build.VERSION.SDK_INT < 16) {
            // On older platforms, collecting tests can fail for large volume of tests.
            // Insert a small delay between each test to prevent this
            builder.addRunListener(new DelayInjector(15 /* msec */));
        }
    }

    private void addListenersFromArg(RunnerArgs args, TestExecutor.Builder builder) {
        for (RunListener listener : args.listeners) {
            builder.addRunListener(listener);
        }
    }

    @Override
    public boolean onException(Object obj, Throwable e) {
        InstrumentationResultPrinter instResultPrinter = getInstrumentationResultPrinter();
        if (instResultPrinter != null) {
            // report better error message back to Instrumentation results.
            instResultPrinter.reportProcessCrash(e);
        }
        return super.onException(obj, e);
    }

    /**
     * Builds a {@link TestRequest} based on given input arguments.
     * <p/>
     */
    // Visible for testing
    TestRequest buildRequest(RunnerArgs runnerArgs, Bundle bundleArgs) {

        TestRequestBuilder builder = createTestRequestBuilder(this, bundleArgs);

        // only scan for tests for current apk aka testContext
        // Note that this represents a change from InstrumentationTestRunner where
        // getTargetContext().getPackageCodePath() aka app under test was also scanned
        builder.addApkToScan(getContext().getPackageCodePath());

        builder.addFromRunnerArgs(runnerArgs);

        if (!runnerArgs.disableAnalytics) {
            if (null != getTargetContext()) {
                UsageTracker tracker = new AnalyticsBasedUsageTracker.Builder(
                        getTargetContext()).buildIfPossible();

                if (null != tracker) {
                    UsageTrackerRegistry.registerInstance(tracker);
                }
            }
        }

        return builder.build();
    }

    /**
     * Factory method for {@link TestRequestBuilder}.
     */
    // Visible for testing
    TestRequestBuilder createTestRequestBuilder(Instrumentation instr, Bundle arguments) {
        return new TestRequestBuilder(instr, arguments);
    }
}
