import { describe, expect, it } from "vitest";
import { validateLogIn, validateSignUp } from "./accountForms";

describe("validateSignUp", () => {
  it("accepts a well-formed signup", () => {
    expect(
      validateSignUp({ email: "host@example.com", password: "correct horse battery", displayName: "Alex Host" }),
    ).toBeNull();
  });

  it("rejects a blank email", () => {
    expect(validateSignUp({ email: "  ", password: "correct horse battery", displayName: "Alex" })).toBe(
      "Email is required.",
    );
  });

  it("rejects a malformed email", () => {
    expect(validateSignUp({ email: "not-an-email", password: "correct horse battery", displayName: "Alex" })).toBe(
      "Enter a valid email address.",
    );
  });

  it("rejects a blank display name", () => {
    expect(validateSignUp({ email: "host@example.com", password: "correct horse battery", displayName: " " })).toBe(
      "Display name is required.",
    );
  });

  it("rejects a password under 8 characters — mirrors SignUpRequest's @Size(min = 8) on the backend", () => {
    expect(validateSignUp({ email: "host@example.com", password: "short", displayName: "Alex" })).toBe(
      "Password must be at least 8 characters.",
    );
  });
});

describe("validateLogIn", () => {
  it("accepts a well-formed login", () => {
    expect(validateLogIn({ email: "host@example.com", password: "whatever-it-is" })).toBeNull();
  });

  it("rejects a blank email", () => {
    expect(validateLogIn({ email: "", password: "whatever-it-is" })).toBe("Email is required.");
  });

  it("rejects a blank password", () => {
    expect(validateLogIn({ email: "host@example.com", password: "" })).toBe("Password is required.");
  });
});
