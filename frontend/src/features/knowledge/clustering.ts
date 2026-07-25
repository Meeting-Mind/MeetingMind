/**
 * 지식 그래프 묶어보기.
 *
 * 연결된 노드끼리 한 덩어리로 모은다. 기준을 "연결 관계"로 잡은 이유는 그래프가
 * 이미 갖고 있는 구조라 별도 계산이나 서버 변경이 필요 없고, 사용자가 화면에서
 * 보는 선과 묶음이 일치하기 때문이다. 임베딩으로 주제를 나누는 방식은 데이터가
 * 조금만 바뀌어도 묶음이 달라져 같은 화면을 다시 봤을 때 혼란스럽다.
 */

export const CLUSTER_MOTION_MS = 1100;

export type Point = { x: number; y: number };

// d3는 링크의 source/target을 문자열, 배열 인덱스(number), 노드 객체 중 어느 것으로도
// 둘 수 있다. 시뮬레이션이 한 번 돌면 객체 참조로 바뀐다.
type Endpoint = string | number | { id: string };
type Linked = { source: Endpoint; target: Endpoint };

function endpointId(value: Endpoint): string {
  return typeof value === "object" && value !== null ? value.id : String(value);
}

/**
 * 연결된 노드에 같은 덩어리 번호를 매긴다.
 *
 * 번호는 노드 순서대로 0부터 붙는다. 매번 같은 입력에 같은 번호가 나와야
 * 색이 흔들리지 않는다.
 */
export function buildClusters(nodeIds: string[], links: Linked[]): Map<string, number> {
  const parent = new Map<string, string>();
  for (const id of nodeIds) {
    parent.set(id, id);
  }

  function find(id: string): string {
    let root = id;
    while (parent.get(root) !== root) {
      root = parent.get(root) as string;
    }
    // 경로 압축. 노드가 많아지면 매 프레임 계산이 부담이 된다.
    let cursor = id;
    while (parent.get(cursor) !== root) {
      const next = parent.get(cursor) as string;
      parent.set(cursor, root);
      cursor = next;
    }
    return root;
  }

  for (const link of links) {
    const left = endpointId(link.source);
    const right = endpointId(link.target);
    if (!parent.has(left) || !parent.has(right)) {
      continue;
    }
    const leftRoot = find(left);
    const rightRoot = find(right);
    if (leftRoot !== rightRoot) {
      parent.set(rightRoot, leftRoot);
    }
  }

  const indexByRoot = new Map<string, number>();
  const result = new Map<string, number>();
  for (const id of nodeIds) {
    const root = find(id);
    if (!indexByRoot.has(root)) {
      indexByRoot.set(root, indexByRoot.size);
    }
    result.set(id, indexByRoot.get(root) as number);
  }
  return result;
}

/**
 * 덩어리 중심을 원형으로 배치한다.
 *
 * 덩어리가 하나면 화면 중앙에 둔다. 하나뿐인데 한쪽으로 밀면 이유 없이
 * 치우쳐 보인다.
 */
export function clusterCenters(count: number, radius: number): Point[] {
  if (count <= 1) {
    return [{ x: 0, y: 0 }];
  }
  const centers: Point[] = [];
  for (let index = 0; index < count; index += 1) {
    const angle = (index / count) * Math.PI * 2 - Math.PI / 2;
    centers.push({ x: Math.cos(angle) * radius, y: Math.sin(angle) * radius });
  }
  return centers;
}

/** 덩어리 안에서 노드를 원형으로 벌린다. 겹치면 몇 개인지 알 수 없다. */
export function slotOffset(indexInCluster: number, clusterSize: number): Point {
  if (clusterSize <= 1) {
    return { x: 0, y: 0 };
  }
  const angle = (indexInCluster / clusterSize) * Math.PI * 2;
  const radius = 26 + clusterSize * 7;
  return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius };
}

/** 시작과 끝이 부드럽다. 소용돌이는 호를 그리므로 튕기는 곡선과 어울리지 않는다. */
export function easeInOut(t: number): number {
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

/**
 * 소용돌이 경로. 직선 이동에 수직 방향 호를 얹는다.
 *
 * 호의 크기는 `sin(pi * t)`라 시작과 끝에서 0이 된다. 그래서 출발점과 도착점은
 * 직선 이동과 같고 중간만 휘어진다. 끝에서 0이 아니면 도착 위치가 어긋난다.
 */
export function swirlPosition(from: Point, to: Point, progress: number, arcSize = 58): Point {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const length = Math.hypot(dx, dy);
  const arc = Math.sin(progress * Math.PI) * arcSize;
  if (length === 0) {
    return { x: from.x, y: from.y };
  }
  return {
    x: from.x + dx * progress + (-dy / length) * arc,
    y: from.y + dy * progress + (dx / length) * arc,
  };
}
