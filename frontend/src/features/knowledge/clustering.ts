import { KNOWLEDGE_LAYER } from "./depth";
import type { KnowledgeKind } from "./types";

/**
 * 지식 그래프 묶어보기.
 *
 * 기준은 셋이다. 모두 노드가 이미 갖고 있는 정보로 계산해 서버 변경이 필요 없고,
 * 같은 데이터에 항상 같은 묶음이 나온다. 임베딩으로 주제를 나누는 방식은 데이터가
 * 조금만 바뀌어도 묶음이 달라져 같은 화면을 다시 봤을 때 혼란스러워 넣지 않았다.
 */

export type ClusterBasis = "link" | "meeting" | "layer";

export const CLUSTER_BASIS_LABELS: Record<ClusterBasis, string> = {
  link: "연결 관계",
  meeting: "회의별",
  layer: "성격별",
};

export const CLUSTER_BASIS_HINTS: Record<ClusterBasis, string> = {
  link: "이어진 노드끼리 묶습니다. 화면의 선과 묶음이 일치합니다.",
  meeting: "같은 회의에서 나온 것끼리 묶습니다. 회의 밖 지식은 따로 모입니다.",
  layer: "공식 지식, 회의 결과물, 회의 원자료로 나눕니다.",
};

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

type Groupable = { id: string; kind: KnowledgeKind; sourceMeetingId: string | null };

/**
 * 회의별 묶음. 같은 회의에서 나온 노드가 한 덩어리다.
 *
 * 회의에 속하지 않은 지식은 모두 한 덩어리로 모은다. 각자 흩어 놓으면 지식이
 * 많을 때 덩어리가 수십 개가 되어 화면이 못 쓰게 된다.
 */
export function clustersByMeeting(nodes: Groupable[]): Map<string, number> {
  const indexByMeeting = new Map<string, number>();
  const result = new Map<string, number>();
  // 회의 없는 지식이 항상 0번을 갖게 먼저 자리를 잡는다. 번호가 흔들리면 색이 바뀐다.
  indexByMeeting.set("", 0);
  for (const node of nodes) {
    const key = node.sourceMeetingId ?? "";
    if (!indexByMeeting.has(key)) {
      indexByMeeting.set(key, indexByMeeting.size);
    }
    result.set(node.id, indexByMeeting.get(key) as number);
  }
  return result;
}

/**
 * 성격별 묶음. 깊이 축과 같은 구분(공식 지식 / 회의 결과물 / 회의 원자료)을 쓴다.
 *
 * 앞층이 0번이 되도록 층 값을 내림차순으로 매긴다. 그래야 덩어리 번호와 화면
 * 앞뒤 순서가 어긋나지 않는다.
 */
export function clustersByLayer(nodes: Groupable[]): Map<string, number> {
  const layers = [...new Set(nodes.map((node) => KNOWLEDGE_LAYER[node.kind] ?? 0))].sort(
    (left, right) => right - left
  );
  const indexByLayer = new Map(layers.map((layer, index) => [layer, index]));
  const result = new Map<string, number>();
  for (const node of nodes) {
    result.set(node.id, indexByLayer.get(KNOWLEDGE_LAYER[node.kind] ?? 0) ?? 0);
  }
  return result;
}

/** 기준에 맞는 묶음을 계산한다. */
export function clustersFor(
  basis: ClusterBasis,
  nodes: Groupable[],
  links: Linked[]
): Map<string, number> {
  if (basis === "meeting") {
    return clustersByMeeting(nodes);
  }
  if (basis === "layer") {
    return clustersByLayer(nodes);
  }
  return buildClusters(
    nodes.map((node) => node.id),
    links
  );
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
