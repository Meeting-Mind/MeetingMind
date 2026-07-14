import { describe, expect, it } from "vitest";
import { parseStoredAuthSession, type AuthSession } from "./session";

const validSession: AuthSession = {
  accessToken: "access-token",
  refreshToken: "refresh-token",
  tokenType: "Bearer",
  expiresIn: 3600,
  refreshExpiresIn: 1209600,
  user: {
    id: "user-1",
    email: "user@example.com",
    displayName: "User",
    status: "active"
  }
};

describe("parseStoredAuthSession", () => {
  it("restores a valid bearer session", () => {
    expect(parseStoredAuthSession(JSON.stringify(validSession))).toEqual(validSession);
  });

  it.each([
    null,
    "not-json",
    JSON.stringify({ ...validSession, accessToken: "" }),
    JSON.stringify({ ...validSession, tokenType: "Basic" }),
    JSON.stringify({ ...validSession, user: { ...validSession.user, id: "" } })
  ])("rejects invalid stored data", (raw) => {
    expect(parseStoredAuthSession(raw)).toBeNull();
  });
});
