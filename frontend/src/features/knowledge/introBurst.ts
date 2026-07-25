/**
 * 지식 그래프 진입 모션.
 *
 * 노드가 한 점에 모여 있다가 제 자리로 팍 퍼진다. 그래프가 "펼쳐진다"는 것을
 * 한 번에 보여주기 위한 연출이다.
 *
 * 시뮬레이션을 그냥 중앙에서 시작하면 힘이 서서히 밀어내 흐물흐물하게 퍼진다.
 * 그래서 최종 배치를 먼저 계산해 두고, 중앙에서 그 자리까지 직접 보간한다.
 * 그러면 "팍" 터지는 느낌이 나오고 끝나는 자리도 시뮬레이션 결과와 같다.
 *
 * 검증 범위: 계산 규칙만 단위 테스트로 고정한다. 화면 e2e는 만들 수 없다 —
 * 그래프 노드는 `embedding_chunks`에서 오므로 색인 worker와 AI 서버가 함께
 * 떠 있어야 하는데 e2e 환경에는 없어 노드가 항상 0개이고, 노드가 0개면
 * 캔버스 자체를 그리지 않는다.
 */

export const INTRO_BURST_MS = 780;

/** 노드마다 조금씩 다른 시점에 튀어나가야 한 덩어리로 보이지 않는다. */
const MAX_STAGGER = 0.28;

type Positioned = { x?: number; y?: number };

export type BurstTarget = { x: number; y: number };

/**
 * 시작이 빠르고 끝에서 살짝 넘어갔다 돌아온다. 기계적인 선형 이동과 달리
 * 튀어나가 자리를 잡는 느낌을 준다.
 */
export function easeOutBack(t: number): number {
  const overshoot = 1.42;
  const shifted = t - 1;
  return 1 + (overshoot + 1) * shifted * shifted * shifted + overshoot * shifted * shifted;
}

/**
 * 노드별 진행도. 시작 시점을 흩어 놓되 모두 같은 시각에 끝나게 맞춘다.
 * 끝을 맞추지 않으면 마지막 노드가 뒤늦게 도착해 모션이 지저분해진다.
 */
export function nodeProgress(elapsedRatio: number, index: number, total: number): number {
  if (total <= 0) {
    return 1;
  }
  const start = total === 1 ? 0 : (index / (total - 1)) * MAX_STAGGER;
  const span = 1 - start;
  if (span <= 0) {
    return 1;
  }
  const local = (elapsedRatio - start) / span;
  if (local <= 0) {
    return 0;
  }
  if (local >= 1) {
    return 1;
  }
  return easeOutBack(local);
}

/**
 * 최종 배치를 미리 계산해 목표 좌표로 남긴다.
 *
 * 좌표가 아직 없는 노드는 원점으로 둔다. 목표가 없으면 보간할 곳이 없어
 * 그 노드만 중앙에 남는데, 그러면 모션이 끝난 뒤에도 한 점에 뭉쳐 보인다.
 */
export function captureTargets<T extends Positioned>(nodes: T[]): BurstTarget[] {
  return nodes.map((node) => ({
    x: Number.isFinite(node.x) ? (node.x as number) : 0,
    y: Number.isFinite(node.y) ? (node.y as number) : 0,
  }));
}

/** 중앙에서 목표까지의 현재 위치. */
export function burstPosition(target: BurstTarget, progress: number): BurstTarget {
  return { x: target.x * progress, y: target.y * progress };
}


/**
 * 진입 모션을 지금 재생해야 하는지.
 *
 * 데이터가 처음 들어올 때 한 번만이다. 그래프 캔버스는 노드나 링크가 바뀔 때마다
 * 다시 초기화되는데, 종류 숨기기나 단일 노드 토글 같은 **필터도 여기에 걸린다.**
 * 그때마다 모션을 다시 틀면 화면이 매번 중앙으로 빨려 들어갔다 나와서 조작이 깨진다.
 */
export function shouldPlayIntro(options: {
  nodeCount: number;
  alreadyPlayed: boolean;
  reduceMotion: boolean;
}): boolean {
  if (options.alreadyPlayed) {
    return false;
  }
  if (options.reduceMotion) {
    return false;
  }
  return options.nodeCount > 0;
}
