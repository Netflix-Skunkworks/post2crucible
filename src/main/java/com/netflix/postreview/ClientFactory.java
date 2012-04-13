package com.netflix.postreview;

import com.atlassian.connector.commons.api.ConnectionCfg;
import com.atlassian.theplugin.commons.exception.HttpProxySettingsException;
import com.atlassian.theplugin.commons.remoteapi.rest.HttpSessionCallbackImpl;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;

/**
 * Implementation of an HttpClient factory to produce a client with the settings we want.
 * @author cquinn
 */
public final class ClientFactory extends HttpSessionCallbackImpl {

    private static MultiThreadedHttpConnectionManager connectionManager;

    private static final int CONNECTION_MANAGER_TIMEOUT = 80000;
    private static final int CONNECTION_TIMEOUT = 30000;
    private static final int DATA_TIMOUT = 10000;

    private static final int TOTAL_MAX_CONNECTIONS = 50;

    private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 3;
    private static int dataTimeout = DATA_TIMOUT;
    private static int connectionTimeout = CONNECTION_TIMEOUT;
    private static int connectionManagerTimeout = CONNECTION_MANAGER_TIMEOUT;

    static {
        connectionManager = new MultiThreadedHttpConnectionManager();
        connectionManager.getParams().setConnectionTimeout(connectionTimeout);
        connectionManager.getParams().setMaxTotalConnections(TOTAL_MAX_CONNECTIONS);
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(DEFAULT_MAX_CONNECTIONS_PER_HOST);
    }

    ClientFactory() {
    }

    public static HttpClient getClient() throws HttpProxySettingsException {
        HttpClient httpClient = new HttpClient(connectionManager);
        httpClient.getParams().setConnectionManagerTimeout(connectionManagerTimeout);
        httpClient.getParams().setSoTimeout(dataTimeout);
        return httpClient;
    }

    public HttpClient getHttpClient(final ConnectionCfg server) throws HttpProxySettingsException {
        final HttpClient client = getClient();
        //client.getParams().setParameter(HttpMethodParams.USER_AGENT, "Crucible review uploader");
        return client;
    }

    public void disposeClient(ConnectionCfg server) {
    }

    public org.apache.commons.httpclient.Cookie[] getCookiesHeaders(ConnectionCfg server) {
        return new org.apache.commons.httpclient.Cookie[0];
    }

}
