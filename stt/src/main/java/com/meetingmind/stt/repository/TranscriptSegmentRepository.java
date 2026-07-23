package com.meetingmind.stt.repository;

import com.meetingmind.stt.domain.TranscriptSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, String> {

    List<TranscriptSegment> findByMeetingIdOrderBySequenceAsc(String meetingId);

    int countByMeetingId(String meetingId);
}
