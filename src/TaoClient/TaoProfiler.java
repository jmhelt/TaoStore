package TaoClient;

import Configuration.TaoConfigs;
import Messages.ClientRequest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TaoProfiler implements Profiler {
    
    protected String mOutputDirectory;

    protected Map<ClientRequest, Long> mReadStartTimes;

    protected DescriptiveStatistics mReadStatistics;

    public TaoProfiler() {
        mOutputDirectory = TaoConfigs.LOG_DIRECTORY;

        mReadStartTimes = new ConcurrentHashMap<>();

        mReadStatistics = new DescriptiveStatistics();
    }

    public void writeStatistics() {
        String report = null;
        String filename = null;
        double lastValue = -1.0;
        int i = 0;

        filename = mOutputDirectory + "/" + "clientReadStats.txt";
        synchronized (mReadStatistics) {
            report = mReadStatistics.toString();
            report += "\nHistogram:\n";

            for (double value : mReadStatistics.getSortedValues()) {
                if (value != lastValue) {
                    if (lastValue != -1.0) {
                        report += String.format("%.2f %d\n", lastValue, i);
                    }
                    i = 0;
                    lastValue = value;
                }
                i++;
            }

            if (mReadStatistics.getN() > 0) {
                report += String.format("%.2f %d\n", lastValue, i);
            }
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
    public void onSendReadToProxy(ClientRequest request) {
        mReadStartTimes.put(request, System.currentTimeMillis());
    }

    @Override
    public void onSendReadToProxyComplete(ClientRequest request) {
        if (mReadStartTimes.containsKey(request)) {
            long readPathStartTime = mReadStartTimes.get(request);
            mReadStartTimes.remove(request);

            long totalTime = System.currentTimeMillis() - readPathStartTime;
            synchronized (mReadStatistics) {
                mReadStatistics.addValue(totalTime);
            }
        }
    }
}
