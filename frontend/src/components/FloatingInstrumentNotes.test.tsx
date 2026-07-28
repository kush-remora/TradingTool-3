import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FloatingInstrumentNotes } from "./FloatingInstrumentNotes";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const deleteJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
  deleteJson: (...args: unknown[]) => deleteJsonMock(...args),
}));

describe("FloatingInstrumentNotes", () => {
  it("keeps note writing behind a floating button and saves the entered text", async () => {
    getJsonMock.mockResolvedValue([]);
    postJsonMock.mockResolvedValue({
      id: 1,
      instrumentToken: 738561,
      notes: "Review the base",
      createdAt: "2026-07-28T10:00:00Z",
      updatedAt: "2026-07-28T10:00:00Z",
    });
    render(<FloatingInstrumentNotes instrumentToken={738561} />);

    expect(screen.queryByLabelText("New note")).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Open research notes"));
    fireEvent.change(await screen.findByLabelText("New note"), { target: { value: "Review the base" } });
    fireEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(postJsonMock).toHaveBeenCalledWith("/api/stocks/notes", {
      instrumentToken: 738561,
      notes: "Review the base",
    }));
    expect(await screen.findByText("Review the base")).toBeInTheDocument();
  });
});
