package com.lion5.cctv.webrtctest;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.freedesktop.gstreamer.Gst;

public class WebRTCTest {
    private static final Logger logger = Logger.getRootLogger();

    private static boolean running;

    public static void main(String[] args) {
        configureLog4j();
        System.setProperty("gstreamer.suppressVersionChecks", "true");
        Gst.init();
        logger.info("Found gstreamer version: " + Gst.getVersionString());
        running = true;
        while (running) {
            WebRTCWebSocket webrtcSendRecv = new WebRTCWebSocket();
            webrtcSendRecv.startCallThread();
            Thread shutdownThread = new Thread(() -> {
                WebRTCTest.running = false;
                webrtcSendRecv.cleanUp();
            }, "ShutdownThread");

            Runtime.getRuntime().addShutdownHook(shutdownThread);

            logger.info("Starting gstreamer...");
            Gst.main();
            webrtcSendRecv.cleanUp();

            Runtime.getRuntime().removeShutdownHook(shutdownThread);
        }
        logger.info("Finished!");
    }

    private static void configureLog4j() {
        try {
            PatternLayout layout = new PatternLayout("%d{ISO8601} %-5p [%t] %c: %m%n");
            ConsoleAppender consoleAppender = new ConsoleAppender(layout);
            logger.addAppender(consoleAppender);
            logger.setLevel(Level.ALL);
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

}
