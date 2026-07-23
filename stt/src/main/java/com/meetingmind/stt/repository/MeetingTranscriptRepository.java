package com.meetingmind.stt.repository;

import com.meetingmind.stt.domain.MeetingTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingTranscriptRepository extends JpaRepository<MeetingTranscript, String> {
}
