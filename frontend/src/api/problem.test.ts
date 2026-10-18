import { describe, expect, it } from "vite-plus/test";
import "./client";
import { listTodos } from "./generated";
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
});

describe("API client", () => {
  it("turns non-JSON error responses into a ProblemDetail", async () => {
    const fetch = async () => new Response("", { status: 502, statusText: "Bad Gateway" });

    const { error } = await listTodos({ baseUrl: "http://localhost", fetch });

    expect(error).toEqual({ type: "about:blank", title: "Bad Gateway", status: 502 });
  });
});
