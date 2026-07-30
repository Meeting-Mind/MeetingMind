import type { UnsupportedReason } from "../../types";

export function reportUnsupportedMessage(reason: UnsupportedReason | null): { title: string; why: string } {
  switch (reason) {
    case "NO_EVIDENCE":
      return {
        title: "회의록을 만들 전사 근거가 없습니다",
        why: "전사가 완료되었는지 확인한 뒤 다시 생성해 주세요.",
      };
    case "LOW_RELEVANCE":
      return {
        title: "회의록으로 정리할 내용이 충분하지 않습니다",
        why: "짧은 대화나 회의와 관련 없는 내용만 있으면 생성하지 않습니다.",
      };
    case "MODEL_UNSUPPORTED":
      return {
        title: "AI가 회의록 형식의 결과를 만들지 못했습니다",
        why: "잠시 후 다시 생성해 주세요. 계속 실패하면 관리자에게 문의해 주세요.",
      };
    case "UNVERIFIED_OUTPUT":
      return {
        title: "생성 결과의 전사 근거를 확인하지 못했습니다",
        why: "근거가 확인되지 않은 내용은 저장하지 않습니다. 다시 생성해 주세요.",
      };
    default:
      return {
        title: "회의록을 만들지 못했습니다",
        why: "실패 원인을 확인할 수 없습니다. 다시 생성해 주세요.",
      };
  }
}
