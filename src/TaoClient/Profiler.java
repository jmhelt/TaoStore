package TaoClient;

public interface Profiler {
    void writeStatistics();

    void onSendReadToProxy(long clientRequestID);

    void onSendReadToProxyComplete(long clientRequestID);
}
