package com.meetingmind.stt.repository;

import com.meetingmind.stt.domain.MeetingSpeaker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingSpeakerRepository extends JpaRepository<MeetingSpeaker, String> {

    Optional<MeetingSpeaker> findByMeetingIdAndLabel(String meetingId, String label);

    List<MeetingSpeaker> findByMeetingId(String meetingId);
}
