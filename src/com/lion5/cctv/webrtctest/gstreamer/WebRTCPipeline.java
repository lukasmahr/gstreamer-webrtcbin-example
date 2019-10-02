package com.lion5.cctv.webrtctest.gstreamer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lion5.cctv.webrtctest.IRemoteMessageSender;
import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.lowlevel.GstAPI;
import org.freedesktop.gstreamer.webrtc.WebRTCBin;
import org.freedesktop.gstreamer.webrtc.WebRTCBin.CREATE_OFFER;
import org.freedesktop.gstreamer.webrtc.WebRTCBin.ON_ICE_CANDIDATE;
import org.freedesktop.gstreamer.webrtc.WebRTCBin.ON_NEGOTIATION_NEEDED;
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType;
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription;

import java.io.IOException;

public class WebRTCPipeline {
    private static final Logger logger = Logger.getLogger(WebRTCPipeline.class);

    private static final String VIDEO_ENC_H264 = "videotestsrc ! video/x-raw,width=1024,height=768,framerate=20/1 ! videoconvert ! x264enc ! rtph264pay ! queue ! capsfilter caps=application/x-rtp,media=video,encoding-name=H264,payload=96";

    private final ObjectMapper mapper = new ObjectMapper();

    private final IRemoteMessageSender remoteMessageSender;

    private WebRTCBin webRTCBin;
    private Pipeline pipe;

    private CREATE_OFFER onOfferCreated = offer -> {
        logger.info("Offer created.");
        webRTCBin.setLocalDescription(offer);
        try {
            JsonNode rootNode = mapper.createObjectNode();
            JsonNode sdpNode = mapper.createObjectNode();
            ((ObjectNode) sdpNode).put("type", "offer");
            ((ObjectNode) sdpNode).put("sdp", offer.getSDPMessage().toString());
            ((ObjectNode) rootNode).set("sdp", sdpNode);
            String json = mapper.writeValueAsString(rootNode);
            logger.info("Sending offer:\n" + json);
            sendMessage(json);
        } catch (JsonProcessingException e) {
            logger.error("Couldn't write JSON: " + e.getMessage());
        }
    };

    private ON_NEGOTIATION_NEEDED onNegotiationNeeded = elem -> {
        logger.info("onNegotiationNeeded: " + elem.getName());

        Thread t = new Thread(() -> executeSynchronized(() -> webRTCBin.createOffer(onOfferCreated)), "OfferThread");
        t.start();
    };

    private ON_ICE_CANDIDATE onIceCandidate = (sdpMLineIndex, candidate) -> {
        JsonNode rootNode = mapper.createObjectNode();
        JsonNode iceNode = mapper.createObjectNode();
        ((ObjectNode) iceNode).put("candidate", candidate);
        ((ObjectNode) iceNode).put("sdpMLineIndex", sdpMLineIndex);
        ((ObjectNode) rootNode).set("ice", iceNode);

        try {
            String json = mapper.writeValueAsString(rootNode);
            logger.info("Local ice candidate: " + json);
            sendMessage(json);
        } catch (JsonProcessingException e) {
            logger.error("Couldn't write JSON: " + e.getMessage());
        }
    };

    public WebRTCPipeline(IRemoteMessageSender remoteMessageSender) {
        this.remoteMessageSender = remoteMessageSender;

        webRTCBin = new WebRTCBin("sendrecv");

        // https://github.com/centricular/gstwebrtc-demos/issues/111
        webRTCBin.set("bundle-policy", 3);

        TransceiverAdded transceiverAdded = (element, object, data) -> {
            object.set("do-nack", true);
            object.set("fec-type", 1);
            object.set("fec-percentage", 100);
        };
        webRTCBin.connect("on-new-transceiver", TransceiverAdded.class, transceiverAdded, transceiverAdded);

        pipe = new Pipeline();

        Bin videoEnc = Gst.parseBinFromDescription(VIDEO_ENC_H264, true);

        pipe.addMany(videoEnc, webRTCBin);
        Element.linkMany(videoEnc, webRTCBin);

        // When the pipeline goes to PLAYING, the on_negotiation_needed()
        // callback will be called, and we will ask webrtcbin to create an offer
        // which will match the pipeline above.
        webRTCBin.connect(onNegotiationNeeded);
        webRTCBin.connect(onIceCandidate);

    }

    private void sendMessage(String msg) {
        remoteMessageSender.sendMessage(msg);
    }

    public void handleSdp(String payload) {
        try (SDPMessage sdpMessage = new SDPMessage()) {
            JsonNode answer = mapper.readTree(payload);
            if (answer.has("sdp")) {
                JsonNode sdpNode = answer.get("sdp").get("sdp");
                String sdpStr = sdpNode.textValue();

                logger.info("answer SDP:\n" + sdpStr);

                sdpMessage.parseBuffer(sdpStr);
                WebRTCSessionDescription description = new WebRTCSessionDescription(WebRTCSDPType.ANSWER, sdpMessage);
                webRTCBin.setRemoteDescription(description);

            } else if (answer.has("ice")) {
                String candidate = answer.get("ice").get("candidate").textValue();
                int sdpMLineIndex = answer.get("ice").get("sdpMLineIndex").intValue();
                logger.info("Got remote ICE candidate: " + candidate);
                webRTCBin.addIceCandidate(sdpMLineIndex, candidate);
            }
        } catch (IOException e) {
            logger.error("Problem reading payload: " + e.getMessage());
        }
    }
    
    private synchronized void executeSynchronized(Runnable runnable) {
        runnable.run();
    }

    public void play() {
        if (pipe != null) {
            executeSynchronized(pipe::play);
        }
    }

    public synchronized void stop() {

        logger.info("Stopping pipeline...");
        if (pipe != null) {
            executeSynchronized(pipe::stop);
        }
        logger.info("Stopping gstreamer...");
        Gst.quit();
        logger.info("Stopped");
    }

    interface TransceiverAdded extends GstAPI.GstCallback {
        public void callback(Element element, GstObject object, Integer data);
    }

}
