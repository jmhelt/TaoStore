package TaoClient;

import Messages.ClientRequest;

import java.net.InetSocketAddress;

public interface Profiler {
    void writeStatistics();

    void onSendReadToProxy(ClientRequest request);

    void onSendReadToProxyComplete(ClientRequest request);
}