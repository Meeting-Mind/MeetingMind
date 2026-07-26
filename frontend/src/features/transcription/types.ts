import type { MeetingDialogueResponse } from "../../types";

export type DialogueStatus = MeetingDialogueResponse["status"];

/** 확정된 transcript 한 줄. DB 저장·회의 기록 대상. */
export type TranscriptRow = MeetingDialogueResponse["rows"][number];

/** 발화 중인 임시 전사. 화면 표시 전용이며 저장 대상이 아니다. */
export type TranscriptPartial = MeetingDialogueResponse["partials"][number];

/**
 * 화면에 그릴 단위. rows(확정)와 partials(임시)를 합치되 isPartial로 구분한다.
 * AGENTS.md STT 규칙: partial을 최종 transcript처럼 취급하지 않는다.
 */
export interface TranscriptEntry {
  key: string;
  speakerId: string;
  speakerName: string;
  startMs: number;
  text: string;
  isPartial: boolean;
}
