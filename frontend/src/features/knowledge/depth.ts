import type { KnowledgeKind } from "./types";

/**
 * 지식 그래프의 깊이(z)축.
 *
 * z는 장식이 아니라 **정보를 담는다.** MeetingMind의 핵심 구분은 "공식 지식"과
 * "회의 기록"이 다른 것이라는 점인데(`PRODUCT.md` Non-Negotiables) 지금은 색으로만
 * 구분한다. 이를 층으로 나눠 공간에 드러낸다.
 *
 * 이 축을 빼면 정보를 잃는다. 그래서 `PRODUCT.md`가 제외한 "장식성 3D"에 해당하지 않는다.
 *
 * 구현은 three.js 없이 원근 투영만 쓴다. 라이브러리를 넣으면 번들이 500~700 kB 늘어나는데,
 * 층 분리에 필요한 것은 깊이에 따른 크기와 흐림뿐이라 그만한 비용을 낼 이유가 없다.
 */

/** 층. 값이 클수록 앞(사용자 쪽)이다. */
export const KNOWLEDGE_LAYER: Record<KnowledgeKind, number> = {
  // 공식 지식 — 사람이 정리해 유지하는 층
  manual: 1,
  glossary: 1,
  external: 1,
  // 확정된 결과물 — 회의에서 건져 올려 공식에 준하는 층
  report: 0,
  decision: 0,
  action: 0,
  // 원자료 — 회의에서 그대로 나온 층
  transcript: -1,
  summary: -1,
};

/**
 * 층 사이 간격(월드 단위).
 *
 * 260에서는 층 차이가 배율 0.78 대 1.0으로 거의 눈에 띄지 않았다. 실제 데이터에
 * 등장하는 종류가 4가지뿐이고 그중 셋이 같은 층이라 대비가 더 약했다.
 */
export const LAYER_GAP = 300;

/** 카메라와 z=0 평면 사이 거리. 작을수록 원근이 강해진다. */
export const FOCAL_LENGTH = 900;

export function layerZ(kind: KnowledgeKind): number {
  return (KNOWLEDGE_LAYER[kind] ?? 0) * LAYER_GAP;
}

/**
 * 원근 배율. 앞의 층은 커지고 뒤의 층은 작아진다.
 *
 * z가 클수록 사용자 쪽이므로 분모에서 **뺀다**. 더하면 부호가 뒤집혀 공식 지식이
 * 뒤로 가고 전사가 앞으로 나온다. 처음 구현이 이 상태였고, 테스트가 그 잘못된
 * 동작을 그대로 단정해 통과하고 있었다.
 *
 * 카메라 평면에 너무 가까우면 배율이 발산하므로 상한을 둔다. 뒤로 멀어질 때는
 * 0으로 수렴해 노드가 사라지므로 하한을 둔다.
 */
export function perspectiveScale(z: number): number {
  const denominator = FOCAL_LENGTH - z;
  if (denominator <= 1) {
    return 1.9;
  }
  return Math.max(0.3, Math.min(1.9, FOCAL_LENGTH / denominator));
}

/**
 * 깊이에 따른 선명도. 앞 층은 또렷하고 뒤 층은 흐리다.
 *
 * 0.42 아래로는 내리지 않는다. 더 흐리면 뒤 층이 "없는 것"처럼 보이는데,
 * 뒤 층(전사·요약)도 읽을 수 있어야 한다.
 */
export function depthOpacity(z: number): number {
  if (z >= 0) {
    return 1;
  }
  const layersBack = Math.abs(z) / LAYER_GAP;
  return Math.max(0.42, 1 - layersBack * 0.3);
}

/** 층 이름. 범례에 쓴다 — 깊이가 무엇을 뜻하는지 말해주지 않으면 장식이 된다. */
export function layerLabel(layer: number): string {
  if (layer > 0) {
    return "공식 지식";
  }
  if (layer < 0) {
    return "회의 원자료";
  }
  return "회의 결과물";
}


/**
 * 연결선의 깊이 반영값.
 *
 * 선은 두 노드를 잇는다. 노드만 흐려지고 선은 그대로면 같은 관계라도 노드가 어느
 * 층에 있느냐에 따라 다르게 보인다.
 *
 * 흐림은 **더 뒤에 있는 쪽**을 따른다. 선 전체가 가장 먼 끝만큼 물러나야 뒤 층의
 * 선이 앞 층 선보다 앞서 보이지 않는다. 굵기는 두 층의 평균을 쓴다 — 한쪽 끝만
 * 따르면 층을 가로지르는 선이 한쪽에서 갑자기 굵어진다.
 */
export function linkDepth(sourceZ: number, targetZ: number): { opacity: number; scale: number } {
  return {
    opacity: Math.min(depthOpacity(sourceZ), depthOpacity(targetZ)),
    scale: (perspectiveScale(sourceZ) + perspectiveScale(targetZ)) / 2,
  };
}
