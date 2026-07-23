package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayRequest;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStatusGatewayResponse;

// Core-side port to the independent Realtime/STT service's internal API; Core never touches STT's DB or code directly.
public interface TranscriptionGateway {

    TranscriptionStartGatewayResponse start(TranscriptionStartGatewayRequest request);

    void stop(String sessionId);

    MeetingTranscriptGatewayResponse transcript(String meetingId);

    TranscriptionStatusGatewayResponse status(String meetingId);
}
