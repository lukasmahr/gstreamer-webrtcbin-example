package com.lion5.cctv.webrtctest;

import com.lion5.cctv.webrtctest.gstreamer.WebRTCPipeline;
import org.apache.log4j.Logger;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.io.IOException;
import java.net.URI;

@WebSocket
public class WebRTCWebSocket implements IRemoteMessageSender {
    private static final Logger logger = Logger.getLogger(WebRTCWebSocket.class);

    private static final String LOCAL_PEER_ID = "564322";
    private static final String REMOTE_PEER_ID = "1";

    private final static String serverUrl = "ws://localhost:8443";
    private Session websocket;

    private WebRTCPipeline pipe;

    

    protected void cleanUp() {
        logger.info("Cleaning up...");
        try {
            if (websocket != null && websocket.isOpen()) {
                websocket.disconnect();
            }
        } catch (IOException e) {
            logger.error("Exception while disconnecting websocket client: " + e.getMessage());
        }
        if (pipe != null) {
            pipe.stop();
        }
    }

    public void startCallThread() {
        Thread t = new Thread(this::startCall, "WebSocket-Call-Thread");
        t.start();
    }

    private synchronized void startCall() {
        WebSocketClient client = new WebSocketClient();
        try {
            client.start();

            URI uri = new URI(serverUrl);
            logger.info("Connecting to: " + uri + "...");
            websocket = client.connect(this, uri, new ClientUpgradeRequest()).get();
            logger.info("Connected to: " + uri);
        } catch (Exception e) {
            logger.error("Exception when trying to start websocket client: " + e.getMessage());
        }
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session websocket) {

        this.pipe = new WebRTCPipeline(this);

        logger.info("Websocket connected...sending HELLO for peer id: " + LOCAL_PEER_ID);
        try {
            websocket.getRemote().sendString("HELLO " + LOCAL_PEER_ID);
        } catch (IOException e) {
            logger.error("IOException when trying to write hello string: " + e.getMessage());
        }
    }

    @OnWebSocketClose
    public void onWebSocketClose(int code, String reason) {
        logger.info("websocket onClose: " + code + " : " + reason);
        cleanUp();
    }

    @OnWebSocketMessage
    public void onWebSocketText(String payload) {
        if (payload.equals("HELLO")) {
            logger.info("Received HELLO...trying to connect to peer.");
            sendMessage("SESSION " + REMOTE_PEER_ID);
        } else if (payload.equals("SESSION_OK")) {
            pipe.play();
        } else if (payload.startsWith("ERROR")) {
            logger.error(payload);
            cleanUp();
        } else {
            pipe.handleSdp(payload);
        }
    }

    @OnWebSocketError
    public void onWebSocketError(Throwable t) {
        logger.error("onError: " + t.getMessage());
        cleanUp();
    }

    @Override
    public synchronized void sendMessage(String msg) {
        if (websocket == null) {
            logger.error("No websocket connection when trying to send Message: " + msg);
            return;
        }
        RemoteEndpoint endpoint = websocket.getRemote();
        if (endpoint == null) {
            logger.error("No remote endpoint when trying to send Message: " + msg);
            return;
        }
        try {
            endpoint.sendString(msg);
        } catch (IOException e) {
            logger.error("IOException when trying to write session string: " + e.getMessage());
        }

    }
}
