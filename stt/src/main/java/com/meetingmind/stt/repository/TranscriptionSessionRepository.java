package com.meetingmind.stt.repository;

import com.meetingmind.stt.domain.TranscriptionSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptionSessionRepository extends JpaRepository<TranscriptionSession, String> {

    Optional<TranscriptionSession> findByRequestId(String requestId);

    // status is persisted as a plain varchar (see TranscriptionSession) — match on the raw enum names.
    List<TranscriptionSession> findByMeetingIdAndStatusIn(String meetingId, List<String> statuses);
}
