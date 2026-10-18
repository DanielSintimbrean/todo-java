import { describe, expect, it } from "vite-plus/test";
import { toProblem } from "./problem";

describe("toProblem", () => {
  it("returns ProblemDetail bodies as they are", () => {
    const problem = { type: "about:blank", title: "Todo not found", status: 404, detail: "Nope" };

    expect(toProblem(problem)).toBe(problem);
  });

  it("wraps network errors", () => {
    expect(toProblem(new TypeError("Failed to fetch"))).toMatchObject({
      title: "Network error",
      detail: "Failed to fetch",
    });
  });

  it("uses plain text bodies as detail", () => {
    expect(toProblem("Bad Gateway")).toMatchObject({
      title: "Unexpected error",
      detail: "Bad Gateway",
    });
  });
});
