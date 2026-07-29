import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FloatingInstrumentNotes } from "./FloatingInstrumentNotes";

describe("FloatingInstrumentNotes", () => {
  it("keeps note writing behind a floating button and saves the entered text", async () => {
    const onAddNote = vi.fn().mockResolvedValue(true);
    render(<FloatingInstrumentNotes notes={[]} loading={false} error={null} onAddNote={onAddNote} onRemoveNote={vi.fn()} />);

    expect(screen.queryByLabelText("New note")).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Open research notes"));
    fireEvent.change(await screen.findByLabelText("New note"), { target: { value: "Review the base" } });
    fireEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(onAddNote).toHaveBeenCalledWith("Review the base"));
  });
});
