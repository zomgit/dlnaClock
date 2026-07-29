package com.dlnaclock.dlna.soap;

import android.util.Log;

import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.rc.RenderingControlService;
import com.dlnaclock.dlna.rc.ConnectionManagerService;
import com.dlnaclock.util.XmlUtil;

public class SoapHandler {

    private static final String TAG = "SoapHandler";

    private AVTransportService avtService;
    private RenderingControlService rcService;
    private ConnectionManagerService cmService;

    public SoapHandler(AVTransportService avtService, RenderingControlService rcService,
                       ConnectionManagerService cmService) {
        this.avtService = avtService;
        this.rcService = rcService;
        this.cmService = cmService;
    }

    public SoapResponse handleRequest(String serviceType, String action, String soapBody) {
        Log.d(TAG, "Handling SOAP action: " + action + " for service: " + serviceType);

        try {
            if (serviceType.contains("AVTransport")) {
                return handleAVTransportAction(action, soapBody);
            } else if (serviceType.contains("RenderingControl")) {
                return handleRenderingControlAction(action, soapBody);
            } else if (serviceType.contains("ConnectionManager")) {
                return handleConnectionManagerAction(action, soapBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling SOAP action: " + action, e);
            return buildErrorResponse(SoapConstants.ERROR_ACTION_FAILED, "Action failed: " + e.getMessage());
        }

        return buildErrorResponse(SoapConstants.ERROR_INVALID_ACTION, "Unknown service type: " + serviceType);
    }

    private SoapResponse handleAVTransportAction(String action, String soapBody) {
        switch (action) {
            case SoapConstants.ACTION_SET_AV_TRANSPORT_URI: {
                String instanceId = XmlUtil.getTagValue(soapBody, "InstanceID");
                String uri = XmlUtil.getTagValue(soapBody, "CurrentURI");
                String metadata = XmlUtil.getTagValue(soapBody, "CurrentURIMetaData");

                Log.i(TAG, "SetAVTransportURI - URI: " + uri);
                if (metadata != null) {
                    Log.d(TAG, "SetAVTransportURI - Metadata length: " + metadata.length());
                }

                int instId = 0;
                try {
                    if (instanceId != null) instId = Integer.parseInt(instanceId);
                } catch (NumberFormatException e) { /* use default */ }

                avtService.setAVTransportURI(instId, uri, metadata);

                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:SetAVTransportURIResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:SetAVTransportURIResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_TRANSPORT_INFO: {
                String[] info = avtService.getTransportInfo();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetTransportInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "<CurrentTransportState>" + info[0] + "</CurrentTransportState>" +
                        "<CurrentTransportStatus>" + info[1] + "</CurrentTransportStatus>" +
                        "<CurrentSpeed>1</CurrentSpeed>" +
                        "</u:GetTransportInfoResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_MEDIA_INFO: {
                String[] mediaInfo = avtService.getMediaInfo();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetMediaInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "<NrTracks>" + mediaInfo[0] + "</NrTracks>" +
                        "<MediaDuration>" + mediaInfo[1] + "</MediaDuration>" +
                        "<CurrentURI>" + XmlUtil.escapeXml(mediaInfo[2]) + "</CurrentURI>" +
                        "<CurrentURIMetaData>" + XmlUtil.escapeXml(mediaInfo[3]) + "</CurrentURIMetaData>" +
                        "<NextURI></NextURI>" +
                        "<NextURIMetaData></NextURIMetaData>" +
                        "<PlayMedium>NETWORK</PlayMedium>" +
                        "<RecordMedium>NOT_IMPLEMENTED</RecordMedium>" +
                        "<WriteStatus>NOT_IMPLEMENTED</WriteStatus>" +
                        "</u:GetMediaInfoResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_POSITION_INFO: {
                String[] posInfo = avtService.getPositionInfo();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetPositionInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "<Track>1</Track>" +
                        "<TrackDuration>" + posInfo[0] + "</TrackDuration>" +
                        "<TrackMetaData></TrackMetaData>" +
                        "<TrackURI>" + XmlUtil.escapeXml(posInfo[1]) + "</TrackURI>" +
                        "<RelTime>" + posInfo[2] + "</RelTime>" +
                        "<AbsTime>" + posInfo[2] + "</AbsTime>" +
                        "<RelCount>0</RelCount>" +
                        "<AbsCount>0</AbsCount>" +
                        "</u:GetPositionInfoResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_PLAY: {
                String speed = XmlUtil.getTagValue(soapBody, "Speed");
                avtService.play(speed != null ? speed : "1");
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:PlayResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:PlayResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_PAUSE: {
                avtService.pause();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:PauseResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:PauseResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_STOP: {
                avtService.stop();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:StopResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:StopResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_SEEK: {
                String unit = XmlUtil.getTagValue(soapBody, "Unit");
                String target = XmlUtil.getTagValue(soapBody, "Target");
                avtService.seek(unit != null ? unit : "REL_TIME", target != null ? target : "00:00:00");
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:SeekResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:SeekResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_NEXT: {
                avtService.next();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:NextResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:NextResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_PREVIOUS: {
                avtService.previous();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:PreviousResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                        "</u:PreviousResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            default:
                return buildErrorResponse(SoapConstants.ERROR_INVALID_ACTION, "Invalid AVTransport action: " + action);
        }
    }

    private SoapResponse handleRenderingControlAction(String action, String soapBody) {
        switch (action) {
            case SoapConstants.ACTION_SET_VOLUME: {
                String volume = XmlUtil.getTagValue(soapBody, "DesiredVolume");
                if (volume != null) {
                    try {
                        rcService.setVolume(Integer.parseInt(volume));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid volume value: " + volume);
                    }
                }
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:SetVolumeResponse xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">" +
                        "</u:SetVolumeResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_VOLUME: {
                int volume = rcService.getVolume();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetVolumeResponse xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">" +
                        "<CurrentVolume>" + volume + "</CurrentVolume>" +
                        "</u:GetVolumeResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_SET_MUTE: {
                String mute = XmlUtil.getTagValue(soapBody, "DesiredMute");
                rcService.setMute("1".equals(mute));
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:SetMuteResponse xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">" +
                        "</u:SetMuteResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_MUTE: {
                boolean mute = rcService.getMute();
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetMuteResponse xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">" +
                        "<CurrentMute>" + (mute ? "1" : "0") + "</CurrentMute>" +
                        "</u:GetMuteResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            default:
                return buildErrorResponse(SoapConstants.ERROR_INVALID_ACTION,
                        "Invalid RenderingControl action: " + action);
        }
    }

    private SoapResponse handleConnectionManagerAction(String action, String soapBody) {
        switch (action) {
            case SoapConstants.ACTION_GET_PROTOCOL_INFO: {
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetProtocolInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:ConnectionManager:1\">" +
                        "<Source>" + SoapConstants.PROTOCOL_INFO_SOURCE + "</Source>" +
                        "<Sink>" + SoapConstants.PROTOCOL_INFO_SINK + "</Sink>" +
                        "</u:GetProtocolInfoResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_CURRENT_CONNECTION_IDS: {
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetCurrentConnectionIDsResponse xmlns:u=\"urn:schemas-upnp-org:service:ConnectionManager:1\">" +
                        "<ConnectionIDs>0</ConnectionIDs>" +
                        "</u:GetCurrentConnectionIDsResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            case SoapConstants.ACTION_GET_CURRENT_CONNECTION_INFO: {
                String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                        "<u:GetCurrentConnectionInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:ConnectionManager:1\">" +
                        "<RcsID>-1</RcsID>" +
                        "<AVTransportID>0</AVTransportID>" +
                        "<ProtocolInfo>" + SoapConstants.PROTOCOL_INFO_SOURCE + "</ProtocolInfo>" +
                        "<PeerConnectionManager></PeerConnectionManager>" +
                        "<PeerConnectionID>-1</PeerConnectionID>" +
                        "<Direction>Input</Direction>" +
                        "<Status>OK</Status>" +
                        "</u:GetCurrentConnectionInfoResponse>" +
                        SoapConstants.SOAP_ENVELOPE_END;
                return new SoapResponse(200, responseXml);
            }

            default:
                return buildErrorResponse(SoapConstants.ERROR_INVALID_ACTION,
                        "Invalid ConnectionManager action: " + action);
        }
    }

    public static SoapResponse buildErrorResponse(int errorCode, String errorDescription) {
        String responseXml = SoapConstants.SOAP_ENVELOPE_START +
                "<s:Fault>" +
                "<faultcode>s:Client</faultcode>" +
                "<faultstring>UPnPError</faultstring>" +
                "<detail>" +
                "<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">" +
                "<errorCode>" + errorCode + "</errorCode>" +
                "<errorDescription>" + XmlUtil.escapeXml(errorDescription) + "</errorDescription>" +
                "</UPnPError>" +
                "</detail>" +
                "</s:Fault>" +
                SoapConstants.SOAP_ENVELOPE_END;
        return new SoapResponse(500, responseXml);
    }

    public static class SoapResponse {
        public final int statusCode;
        public final String body;

        public SoapResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    // Parse SOAP action from SOAPACTION header or body
    public static String parseAction(String soapActionHeader, String soapBody) {
        // Try SOAPACTION header first
        if (soapActionHeader != null && !soapActionHeader.isEmpty()) {
            // Format: "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI"
            int hashIdx = soapActionHeader.indexOf('#');
            if (hashIdx >= 0) {
                String action = soapActionHeader.substring(hashIdx + 1).replace("\"", "").trim();
                if (!action.isEmpty()) return action;
            }
        }

        // Try parsing from body
        if (soapBody != null) {
            // Look for action element in body
            int bodyStart = soapBody.indexOf("<s:Body>");
            if (bodyStart < 0) bodyStart = soapBody.indexOf("<Body>");
            if (bodyStart >= 0) {
                int actionStart = soapBody.indexOf("urn:schemas-upnp-org:service:", bodyStart);
                if (actionStart >= 0) {
                    int actionEnd = soapBody.indexOf(">", actionStart);
                    if (actionEnd >= 0) {
                        String actionElement = soapBody.substring(actionStart, actionEnd);
                        int hashIdx = actionElement.indexOf('#');
                        if (hashIdx >= 0) {
                            return actionElement.substring(hashIdx + 1).trim();
                        }
                    }
                }
            }
        }

        return null;
    }

    // Parse service type from SOAPACTION header
    public static String parseServiceType(String soapActionHeader) {
        if (soapActionHeader != null && !soapActionHeader.isEmpty()) {
            int hashIdx = soapActionHeader.indexOf('#');
            if (hashIdx >= 0) {
                return soapActionHeader.substring(1, hashIdx).replace("\"", "").trim();
            }
        }
        return null;
    }
}
