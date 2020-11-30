package TaoClient;

import Configuration.TaoConfigs;
import Messages.ClientRequest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TaoProfiler implements Profiler {

    private long nExcludedRequests = 100;

    protected String mOutputDirectory;

    protected Map<Long, Long> mReadStartTimes;

    protected DescriptiveStatistics mReadStatistics;

    public TaoProfiler() {
        mOutputDirectory = TaoConfigs.LOG_DIRECTORY;

        mReadStartTimes = new ConcurrentHashMap<>();

        mReadStatistics = new DescriptiveStatistics();
    }

    private String histogramString(DescriptiveStatistics ds) {
        double lastValue = -1.0;
        int n = 0;

        String hist = "Histogram:\n";

        for (double value : ds.getSortedValues()) {
            if (value != lastValue) {
                if (lastValue != -1.0) {
                    hist += String.format("%.2f %d\n", lastValue, n);
                }
                n = 0;
                lastValue = value;
            }
            n++;
        }

        if (ds.getN() > 0) {
            hist += String.format("%.2f %d\n", lastValue, n);
        }

        return hist;
    }

    public void writeStatistics() {
        String report = null;
        String filename = null;
        double lastValue = -1.0;
        int i = 0;

        filename = mOutputDirectory + "/" + "clientReadStats.txt";
        synchronized (mReadStatistics) {
            report = mReadStatistics.toString();
            report += "\n";
            report += histogramString(mReadStatistics);
        }
        // Write the report to a file
        try {
            PrintWriter writer = new PrintWriter(filename);
            writer.println(report);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSendReadToProxy(long clientRequestID) {
        if (clientRequestID >= nExcludedRequests) {
            mReadStartTimes.put(clientRequestID, System.currentTimeMillis());
        }
    }

    @Override
    public void onSendReadToProxyComplete(long clientRequestID) {
        if (mReadStartTimes.containsKey(clientRequestID)) {
            long readPathStartTime = mReadStartTimes.get(clientRequestID);
            mReadStartTimes.remove(clientRequestID);

            long totalTime = System.currentTimeMillis() - readPathStartTime;
            synchronized (mReadStatistics) {
                mReadStatistics.addValue(totalTime);
            }
        }
    }
}
