import { describe, expect, it } from "vitest";
import { burstPosition, captureTargets, easeOutBack, nodeProgress, shouldPlayIntro } from "./introBurst";

describe("easeOutBack", () => {
  it("시작과 끝을 정확히 맞춘다", () => {
    expect(easeOutBack(0)).toBeCloseTo(0, 5);
    expect(easeOutBack(1)).toBeCloseTo(1, 5);
  });

  it("끝에서 1을 넘어섰다가 돌아온다", () => {
    // 넘어서지 않으면 기계적인 이동이 되어 "팍" 튀는 느낌이 나지 않는다.
    expect(easeOutBack(0.7)).toBeGreaterThan(1);
  });

  it("초반이 빠르다", () => {
    expect(easeOutBack(0.3)).toBeGreaterThan(0.3);
  });
});

describe("nodeProgress", () => {
  it("모든 노드가 같은 시각에 끝난다", () => {
    // 끝이 어긋나면 마지막 노드가 뒤늦게 도착해 모션이 지저분해진다.
    for (let index = 0; index < 5; index += 1) {
      expect(nodeProgress(1, index, 5)).toBe(1);
    }
  });

  it("시작 시점을 흩어 놓는다", () => {
    // 전부 같이 출발하면 한 덩어리로 보인다.
    const first = nodeProgress(0.1, 0, 5);
    const last = nodeProgress(0.1, 4, 5);
    expect(first).toBeGreaterThan(last);
  });

  it("시작 전에는 0이다", () => {
    expect(nodeProgress(0, 4, 5)).toBe(0);
  });

  it("노드가 하나뿐이면 지연 없이 시작한다", () => {
    expect(nodeProgress(0.5, 0, 1)).toBeGreaterThan(0);
  });

  it("노드가 없으면 끝난 것으로 본다", () => {
    // 0으로 나누면 NaN이 되고 위치가 사라진다.
    expect(nodeProgress(0.5, 0, 0)).toBe(1);
  });
});

describe("captureTargets", () => {
  it("좌표를 그대로 목표로 삼는다", () => {
    expect(captureTargets([{ x: 12, y: -30 }])).toEqual([{ x: 12, y: -30 }]);
  });

  it("좌표가 없거나 숫자가 아니면 원점으로 둔다", () => {
    // 목표가 NaN이면 그 노드만 화면에서 사라진다.
    expect(captureTargets([{}, { x: Number.NaN, y: 3 }])).toEqual([
      { x: 0, y: 0 },
      { x: 0, y: 3 },
    ]);
  });
});

describe("burstPosition", () => {
  it("진행도 0이면 중앙이다", () => {
    // 음수 좌표에 0을 곱하면 -0이 된다. 화면상 같은 자리이므로 값으로 비교한다.
    const at0 = burstPosition({ x: 100, y: -50 }, 0);
    expect(at0.x).toBeCloseTo(0, 10);
    expect(at0.y).toBeCloseTo(0, 10);
  });

  it("진행도 1이면 목표와 같다", () => {
    expect(burstPosition({ x: 100, y: -50 }, 1)).toEqual({ x: 100, y: -50 });
  });
});


describe("shouldPlayIntro", () => {
  const base = { nodeCount: 5, alreadyPlayed: false, reduceMotion: false };

  it("데이터가 처음 들어오면 재생한다", () => {
    expect(shouldPlayIntro(base)).toBe(true);
  });

  it("이미 재생했으면 다시 틀지 않는다", () => {
    // 필터(종류 숨기기, 단일 노드 토글)도 캔버스를 다시 초기화한다. 그때마다
    // 모션을 틀면 화면이 중앙으로 빨려 들어갔다 나와서 조작이 깨진다.
    expect(shouldPlayIntro({ ...base, alreadyPlayed: true })).toBe(false);
  });

  it("노드가 없으면 재생하지 않는다", () => {
    expect(shouldPlayIntro({ ...base, nodeCount: 0 })).toBe(false);
  });

  it("움직임 줄이기를 켰으면 재생하지 않는다", () => {
    expect(shouldPlayIntro({ ...base, reduceMotion: true })).toBe(false);
  });
});
