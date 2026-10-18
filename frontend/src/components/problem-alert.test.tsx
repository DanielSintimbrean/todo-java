import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vite-plus/test";
import { ProblemAlert } from "./problem-alert";

afterEach(cleanup);

describe("ProblemAlert", () => {
  it("shows title, status, detail and field errors", () => {
    render(
      <ProblemAlert
        error={{
          type: "about:blank",
          title: "Bad Request",
          status: 400,
          detail: "Validation failed",
          errors: { title: "must not be blank" },
        }}
      />,
    );

    expect(screen.getByRole("alert").textContent).toContain("Bad Request (400)");
    expect(screen.getByText("Validation failed")).toBeTruthy();
    expect(screen.getByText("title").parentElement?.textContent).toBe("title: must not be blank");
  });
});
