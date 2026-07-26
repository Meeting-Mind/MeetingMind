import { describe, expect, it } from "vitest";
import {
  buildClusters,
  clusterCenters,
  clustersByLayer,
  clustersByMeeting,
  clustersFor,
  easeInOut,
  slotOffset,
  swirlPosition,
} from "./clustering";
import type { KnowledgeKind } from "./types";

describe("buildClusters", () => {
  it("연결된 노드를 같은 덩어리로 묶는다", () => {
    const clusters = buildClusters(["a", "b", "c"], [{ source: "a", target: "b" }]);

    expect(clusters.get("a")).toBe(clusters.get("b"));
    expect(clusters.get("c")).not.toBe(clusters.get("a"));
  });

  it("간접 연결도 하나로 묶는다", () => {
    // a-b, b-c면 a와 c도 같은 덩어리다. 직접 연결만 보면 세 덩어리가 된다.
    const clusters = buildClusters(
      ["a", "b", "c"],
      [{ source: "a", target: "b" }, { source: "b", target: "c" }]
    );

    expect(clusters.get("a")).toBe(clusters.get("c"));
  });

  it("연결이 없으면 각자 다른 덩어리다", () => {
    const clusters = buildClusters(["a", "b"], []);
    expect(clusters.get("a")).not.toBe(clusters.get("b"));
  });

  it("링크 객체가 노드 참조로 바뀌어 있어도 처리한다", () => {
    // d3가 시뮬레이션을 돌리면 source/target을 문자열에서 노드 객체로 바꾼다.
    const clusters = buildClusters(["a", "b"], [{ source: { id: "a" }, target: { id: "b" } }]);
    expect(clusters.get("a")).toBe(clusters.get("b"));
  });

  it("노드 목록에 없는 링크는 무시한다", () => {
    // 필터로 노드를 숨기면 링크만 남을 수 있다. 그때 터지면 안 된다.
    expect(() => buildClusters(["a"], [{ source: "a", target: "사라진노드" }])).not.toThrow();
  });

  it("같은 입력에 같은 번호를 준다", () => {
    // 번호가 흔들리면 덩어리 색이 매번 바뀐다.
    const first = buildClusters(["a", "b", "c"], [{ source: "a", target: "b" }]);
    const second = buildClusters(["a", "b", "c"], [{ source: "a", target: "b" }]);
    expect([...first.entries()]).toEqual([...second.entries()]);
  });
});

describe("clusterCenters", () => {
  it("덩어리가 하나면 중앙에 둔다", () => {
    // 하나뿐인데 한쪽으로 밀면 이유 없이 치우쳐 보인다.
    expect(clusterCenters(1, 200)).toEqual([{ x: 0, y: 0 }]);
  });

  it("여러 덩어리를 같은 반지름 위에 배치한다", () => {
    const centers = clusterCenters(4, 200);
    expect(centers).toHaveLength(4);
    for (const center of centers) {
      expect(Math.hypot(center.x, center.y)).toBeCloseTo(200, 5);
    }
  });

  it("덩어리 중심이 서로 겹치지 않는다", () => {
    const centers = clusterCenters(3, 200);
    expect(Math.hypot(centers[0].x - centers[1].x, centers[0].y - centers[1].y)).toBeGreaterThan(1);
  });
});

describe("slotOffset", () => {
  it("혼자면 중심에 둔다", () => {
    expect(slotOffset(0, 1)).toEqual({ x: 0, y: 0 });
  });

  it("같은 덩어리 안에서 서로 다른 자리를 준다", () => {
    // 겹치면 몇 개인지 알 수 없다.
    const first = slotOffset(0, 4);
    const second = slotOffset(1, 4);
    expect(Math.hypot(first.x - second.x, first.y - second.y)).toBeGreaterThan(1);
  });

  it("덩어리가 클수록 넓게 벌어진다", () => {
    expect(Math.hypot(...Object.values(slotOffset(0, 10)) as [number, number]))
      .toBeGreaterThan(Math.hypot(...Object.values(slotOffset(0, 3)) as [number, number]));
  });
});

describe("swirlPosition", () => {
  const from = { x: 0, y: 0 };
  const to = { x: 100, y: 0 };

  it("시작과 끝은 직선 이동과 같다", () => {
    // 끝에서 호가 0이 아니면 도착 위치가 어긋난다.
    expect(swirlPosition(from, to, 0)).toEqual({ x: 0, y: 0 });
    const end = swirlPosition(from, to, 1);
    expect(end.x).toBeCloseTo(100, 5);
    expect(end.y).toBeCloseTo(0, 5);
  });

  it("중간에는 경로에서 벗어난다", () => {
    // 벗어나지 않으면 직선 이동과 구분되지 않는다.
    expect(Math.abs(swirlPosition(from, to, 0.5).y)).toBeGreaterThan(10);
  });

  it("출발점과 도착점이 같으면 그대로 둔다", () => {
    // 거리가 0이면 방향을 구할 수 없어 NaN이 된다.
    const same = swirlPosition(from, { x: 0, y: 0 }, 0.5);
    expect(Number.isNaN(same.x)).toBe(false);
    expect(Number.isNaN(same.y)).toBe(false);
  });
});

describe("easeInOut", () => {
  it("시작과 끝을 정확히 맞춘다", () => {
    expect(easeInOut(0)).toBeCloseTo(0, 5);
    expect(easeInOut(1)).toBeCloseTo(1, 5);
  });

  it("1을 넘지 않는다", () => {
    // 소용돌이는 호를 그리므로 튕기는 곡선과 겹치면 경로가 요동친다.
    for (let step = 0; step <= 20; step += 1) {
      expect(easeInOut(step / 20)).toBeLessThanOrEqual(1);
    }
  });
});


function node(id: string, kind: KnowledgeKind, sourceMeetingId: string | null = null) {
  return { id, kind, sourceMeetingId };
}

describe("clustersByMeeting", () => {
  it("같은 회의에서 나온 것끼리 묶는다", () => {
    const clusters = clustersByMeeting([
      node("a", "decision", "m1"),
      node("b", "action", "m1"),
      node("c", "decision", "m2"),
    ]);

    expect(clusters.get("a")).toBe(clusters.get("b"));
    expect(clusters.get("c")).not.toBe(clusters.get("a"));
  });

  it("회의에 속하지 않은 지식은 한 덩어리로 모은다", () => {
    // 각자 흩어 놓으면 지식이 많을 때 덩어리가 수십 개가 되어 화면을 못 쓴다.
    const clusters = clustersByMeeting([
      node("k1", "manual"),
      node("k2", "manual"),
      node("k3", "external"),
    ]);

    expect(new Set(clusters.values()).size).toBe(1);
  });

  it("회의 없는 지식이 항상 0번이다", () => {
    // 번호가 흔들리면 같은 화면을 다시 볼 때 덩어리 색이 바뀐다.
    const withMeetingFirst = clustersByMeeting([node("a", "decision", "m1"), node("k", "manual")]);
    const withKnowledgeFirst = clustersByMeeting([node("k", "manual"), node("a", "decision", "m1")]);

    expect(withMeetingFirst.get("k")).toBe(0);
    expect(withKnowledgeFirst.get("k")).toBe(0);
  });
});

describe("clustersByLayer", () => {
  it("성격이 같으면 한 덩어리다", () => {
    const clusters = clustersByLayer([node("k1", "manual"), node("k2", "glossary")]);
    expect(clusters.get("k1")).toBe(clusters.get("k2"));
  });

  it("공식 지식과 회의 원자료를 나눈다", () => {
    const clusters = clustersByLayer([node("k", "manual"), node("t", "transcript")]);
    expect(clusters.get("k")).not.toBe(clusters.get("t"));
  });

  it("앞층이 0번이다", () => {
    // 덩어리 번호와 화면 앞뒤 순서가 어긋나면 색과 깊이가 따로 논다.
    const clusters = clustersByLayer([
      node("t", "transcript"),
      node("r", "report"),
      node("k", "manual"),
    ]);

    expect(clusters.get("k")).toBe(0);
    expect(clusters.get("t")).toBe(2);
  });
});

describe("clustersFor", () => {
  const nodes = [node("a", "decision", "m1"), node("b", "manual")];

  it("기준마다 다른 묶음을 준다", () => {
    const byMeeting = clustersFor("meeting", nodes, []);
    const byLayer = clustersFor("layer", nodes, []);
    const byLink = clustersFor("link", nodes, [{ source: "a", target: "b" }]);

    // 회의별: 회의 결과물과 지식이 갈린다
    expect(byMeeting.get("a")).not.toBe(byMeeting.get("b"));
    // 성격별: 회의 결과물과 공식 지식이 갈린다
    expect(byLayer.get("a")).not.toBe(byLayer.get("b"));
    // 연결 관계: 이어져 있으니 한 덩어리다
    expect(byLink.get("a")).toBe(byLink.get("b"));
  });

  it("모르는 기준은 연결 관계로 처리한다", () => {
    const clusters = clustersFor("link", nodes, []);
    expect(clusters.size).toBe(2);
  });
});
