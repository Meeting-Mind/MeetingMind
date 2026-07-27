package com.meetingmind.stt.repository;

import com.meetingmind.stt.domain.MeetingTranscript;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingTranscriptRepository extends JpaRepository<MeetingTranscript, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transcript from MeetingTranscript transcript where transcript.meetingId = :meetingId")
    Optional<MeetingTranscript> findByMeetingIdForUpdate(@Param("meetingId") String meetingId);
}
