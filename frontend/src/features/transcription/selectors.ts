import type { MeetingDialogueResponse } from "../../types";
import type { TranscriptEntry } from "./types";

function normalizeForCompare(value: string): string {
  return value.replace(/\s+/g, " ").trim().toLowerCase();
}

const INTERNAL_STT_SPEAKER_PATTERN = /^stt-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function visibleSpeakerName(speakerName: string | null, speakerLabel: string): string {
  const name = speakerName?.trim();
  if (name && !INTERNAL_STT_SPEAKER_PATTERN.test(name)) {
    return name;
  }
  return INTERNAL_STT_SPEAKER_PATTERN.test(speakerLabel) ? "참여자" : speakerLabel;
}

/**
 * rows(확정) + partials(임시)를 화면용 엔트리로 합친다.
 *
 * - rows는 startMs 오름차순으로 정렬한다.
 * - partial key는 speakerLabel 기준으로 안정적으로 유지한다.
 *   (배열 index를 key로 쓰면 발화 순서가 바뀔 때 DOM 재사용이 어긋난다)
 * - 이미 확정된 마지막 발화와 동일한 partial은 중복이므로 버린다.
 *   폴링 간격 사이에 final이 확정되면 같은 문장이 두 번 보이는 현상을 막는다.
 */
export function buildTranscriptEntries(dialogue: MeetingDialogueResponse | undefined): TranscriptEntry[] {
  if (!dialogue) {
    return [];
  }

  const rows = [...dialogue.rows].sort((left, right) => left.startMs - right.startMs);

  // 화자별 마지막 확정 발화 — partial 중복 판별에 사용한다.
  const lastFinalBySpeaker = new Map<string, string>();
  for (const row of rows) {
    lastFinalBySpeaker.set(row.speakerLabel, normalizeForCompare(row.text));
  }

  const entries: TranscriptEntry[] = rows.map((row) => ({
    key: row.segmentId,
    speakerId: row.speakerId,
    speakerName: visibleSpeakerName(row.speakerName, row.speakerLabel),
    startMs: row.startMs,
    text: row.text,
    isPartial: false
  }));

  const seenPartialSpeakers = new Set<string>();
  for (const partial of dialogue.partials) {
    const text = normalizeForCompare(partial.text);
    if (text.length === 0) {
      continue;
    }
    if (lastFinalBySpeaker.get(partial.speakerLabel) === text) {
      continue;
    }
    // 같은 화자의 partial이 여러 개 오면 첫 번째만 사용한다.
    if (seenPartialSpeakers.has(partial.speakerLabel)) {
      continue;
    }
    seenPartialSpeakers.add(partial.speakerLabel);
    entries.push({
      key: `partial:${partial.speakerLabel}`,
      speakerId: partial.speakerLabel,
      speakerName: visibleSpeakerName(partial.speakerName, partial.speakerLabel),
      startMs: Number.MAX_SAFE_INTEGER,
      text: partial.text,
      isPartial: true
    });
  }

  return entries;
}

export function filterTranscriptEntries(entries: TranscriptEntry[], search: string): TranscriptEntry[] {
  const query = search.trim().toLowerCase();
  if (query.length === 0) {
    return entries;
  }
  return entries.filter(
    (entry) =>
      entry.text.toLowerCase().includes(query) || entry.speakerName.toLowerCase().includes(query)
  );
}
