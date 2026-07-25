import { describe, expect, it } from "vitest";
import {
  FOCAL_LENGTH,
  KNOWLEDGE_LAYER,
  LAYER_GAP,
  depthOpacity,
  layerLabel,
  layerZ,
  linkDepth,
  perspectiveScale,
} from "./depth";
import type { KnowledgeKind } from "./types";

const ALL_KINDS: KnowledgeKind[] = [
  "report",
  "decision",
  "manual",
  "external",
  "glossary",
  "action",
  "transcript",
  "summary",
];

describe("KNOWLEDGE_LAYER", () => {
  it("모든 종류에 층이 정해져 있다", () => {
    // 빠진 종류가 있으면 그 노드만 z=0으로 떨어져 층 구분이 무너진다.
    for (const kind of ALL_KINDS) {
      expect(KNOWLEDGE_LAYER[kind], `${kind}에 층이 없다`).toBeDefined();
    }
  });

  it("공식 지식이 회의 원자료보다 앞이다", () => {
    // 이 순서가 뒤집히면 깊이가 뜻하는 바가 반대가 된다.
    expect(KNOWLEDGE_LAYER.manual).toBeGreaterThan(KNOWLEDGE_LAYER.transcript);
    expect(KNOWLEDGE_LAYER.glossary).toBeGreaterThan(KNOWLEDGE_LAYER.summary);
  });

  it("회의 결과물은 두 층 사이에 있다", () => {
    expect(KNOWLEDGE_LAYER.report).toBeLessThan(KNOWLEDGE_LAYER.manual);
    expect(KNOWLEDGE_LAYER.report).toBeGreaterThan(KNOWLEDGE_LAYER.transcript);
  });
});

describe("layerZ", () => {
  it("같은 층이면 같은 z다", () => {
    expect(layerZ("manual")).toBe(layerZ("glossary"));
    expect(layerZ("transcript")).toBe(layerZ("summary"));
  });

  it("층 간격만큼 벌어진다", () => {
    expect(layerZ("manual") - layerZ("report")).toBe(LAYER_GAP);
  });
});

describe("perspectiveScale", () => {
  it("z=0에서 배율이 1이다", () => {
    expect(perspectiveScale(0)).toBeCloseTo(1, 5);
  });

  it("앞 층은 커지고 뒤 층은 작아진다", () => {
    // 처음 구현이 이 부호를 반대로 두어 공식 지식이 뒤로 갔다. 그때 이 테스트가
    // 잘못된 동작을 그대로 단정해 통과했다. 방향을 명시적으로 고정한다.
    expect(perspectiveScale(layerZ("manual"))).toBeGreaterThan(1);
    expect(perspectiveScale(layerZ("transcript"))).toBeLessThan(1);
  });

  it("층 순서와 배율 순서가 일치한다", () => {
    const front = perspectiveScale(layerZ("manual"));
    const middle = perspectiveScale(layerZ("report"));
    const back = perspectiveScale(layerZ("transcript"));
    expect(front).toBeGreaterThan(middle);
    expect(middle).toBeGreaterThan(back);
  });

  it("층 차이가 눈에 띌 만큼 벌어진다", () => {
    // 간격이 좁으면 배율 차이가 0.78 대 1.0 수준이라 사실상 보이지 않는다.
    const front = perspectiveScale(layerZ("manual"));
    const back = perspectiveScale(layerZ("transcript"));
    expect(front - back).toBeGreaterThan(0.5);
  });

  it("카메라 뒤로 넘어가도 뒤집히지 않는다", () => {
    // 음수 배율이면 노드가 반대편에 그려진다.
    expect(perspectiveScale(-FOCAL_LENGTH * 2)).toBeGreaterThan(0);
    expect(perspectiveScale(FOCAL_LENGTH * 2)).toBeGreaterThan(0);
  });

  it("배율에 상한과 하한이 있다", () => {
    // 하한이 없으면 먼 노드가 0으로 수렴해 사라지고, 상한이 없으면 가까운 노드가 발산한다.
    expect(perspectiveScale(-100000)).toBeGreaterThanOrEqual(0.3);
    expect(perspectiveScale(FOCAL_LENGTH)).toBeLessThanOrEqual(1.9);
  });
});

describe("depthOpacity", () => {
  it("앞 층은 완전히 또렷하다", () => {
    expect(depthOpacity(layerZ("manual"))).toBe(1);
    expect(depthOpacity(0)).toBe(1);
  });

  it("뒤로 갈수록 흐려진다", () => {
    expect(depthOpacity(layerZ("transcript"))).toBeLessThan(1);
  });

  it("아무리 멀어도 읽을 수 있을 만큼은 남긴다", () => {
    // 뒤 층(전사·요약)도 내용을 확인해야 한다. 사라지면 안 된다.
    expect(depthOpacity(-100000)).toBeGreaterThanOrEqual(0.42);
  });
});

describe("layerLabel", () => {
  it("층마다 이름이 다르다", () => {
    // 깊이가 무엇을 뜻하는지 말해주지 않으면 장식이 된다.
    const labels = new Set([layerLabel(1), layerLabel(0), layerLabel(-1)]);
    expect(labels.size).toBe(3);
  });
});


describe("linkDepth", () => {
  it("앞 층끼리 이은 선은 또렷하다", () => {
    expect(linkDepth(layerZ("manual"), layerZ("manual")).opacity).toBe(1);
  });

  it("뒤 층이 섞이면 더 뒤를 따른다", () => {
    // 선 전체가 가장 먼 끝만큼 물러나야 뒤 층 선이 앞 층 선보다 앞서 보이지 않는다.
    const crossing = linkDepth(layerZ("manual"), layerZ("transcript"));
    const backOnly = linkDepth(layerZ("transcript"), layerZ("transcript"));
    expect(crossing.opacity).toBe(backOnly.opacity);
  });

  it("굵기는 두 층의 중간이다", () => {
    // 한쪽 끝만 따르면 층을 가로지르는 선이 한쪽에서 갑자기 굵어진다.
    const crossing = linkDepth(layerZ("manual"), layerZ("transcript"));
    const front = linkDepth(layerZ("manual"), layerZ("manual"));
    const back = linkDepth(layerZ("transcript"), layerZ("transcript"));
    expect(crossing.scale).toBeLessThan(front.scale);
    expect(crossing.scale).toBeGreaterThan(back.scale);
  });

  it("같은 층이면 노드와 같은 배율이다", () => {
    // 선과 노드가 따로 놀면 선이 노드에서 떨어져 보인다.
    const z = layerZ("report");
    expect(linkDepth(z, z).scale).toBeCloseTo(perspectiveScale(z), 10);
  });
});
