import { useState } from "react";
import type { TicketRequest } from "../types/ticket";

interface TicketFiltersProps {
  request: TicketRequest;
  onApply: (request: TicketRequest) => void;
}

const columns = [
  { field: "id", label: "ID", input: "number", min: 1 },
  { field: "name", label: "Name", input: "text" },
  { field: "creationDate", label: "Created", input: "date" },
  { field: "venueId", label: "Venue", input: "number", min: 1 },
  { field: "trainSetId", label: "Train set", input: "number", min: 1 },
  { field: "carriageNumber", label: "Carriage", input: "text" },
  { field: "seatNumber", label: "Seat", input: "text" },
  { field: "basePrice", label: "Price", input: "number", min: 0 },
  { field: "discount", label: "Discount", input: "number", min: 0 },
  { field: "type", label: "Type", input: "select" },
  { field: "refundable", label: "Refundable", input: "select" },
] as const;

export function TicketFilters({ request, onApply }: TicketFiltersProps) {
  const [draft, setDraft] = useState<Record<string, string>>({});

  function apply(field: string, value: string) {
    const next = { ...request, page: 1 };
    const trimmed = value.trim();
    if (!trimmed) {
      delete next[field as keyof TicketRequest];
    } else {
      const column = columns.find((item) => item.field === field);
      const parsed =
        column?.input === "number"
          ? Number(trimmed)
          : field === "refundable"
            ? trimmed === "true"
            : trimmed;
      Object.assign(next, { [field]: parsed });
    }
    if (
      next[field as keyof TicketRequest] !==
      request[field as keyof TicketRequest]
    )
      onApply(next);
  }

  return (
    <tr className="table-filter-header">
      {columns.map((column) => {
        const value =
          draft[column.field] ?? String(request[column.field] ?? "");
        const [sortField, direction] = (request.sort ?? "id,asc").split(",");
        return (
          <th key={column.field} scope="col">
            <button
              type="button"
              className="column-sort"
              onClick={() =>
                onApply({
                  ...request,
                  page: 1,
                  sort: `${column.field},${sortField === column.field && direction === "asc" ? "desc" : "asc"}`,
                })
              }
            >
              {column.label}{" "}
              {sortField === column.field
                ? direction === "asc"
                  ? "↑"
                  : "↓"
                : "↕"}
            </button>
            {column.input === "select" ? (
              <select
                aria-label={`Filter ${column.label}`}
                value={value}
                onChange={(event) => {
                  setDraft((current) => ({
                    ...current,
                    [column.field]: event.target.value,
                  }));
                  apply(column.field, event.target.value);
                }}
              >
                <option value="">Any</option>
                {column.field === "type" ? (
                  ["VIP", "USUAL", "CHEAP"].map((type) => (
                    <option key={type}>{type}</option>
                  ))
                ) : (
                  <>
                    <option value="true">Yes</option>
                    <option value="false">No</option>
                  </>
                )}
              </select>
            ) : (
              <input
                aria-label={`Filter ${column.label}`}
                type={column.input}
                placeholder={
                  column.field === "venueId" ? "Venue ID" : "Search…"
                }
                min={"min" in column ? column.min : undefined}
                step={
                  column.field === "basePrice"
                    ? "any"
                    : column.input === "number"
                      ? 1
                      : undefined
                }
                value={value}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    [column.field]: event.target.value,
                  }))
                }
                onBlur={(event) => {
                  if (event.target.validity.valid)
                    apply(column.field, event.target.value);
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    event.currentTarget.reportValidity();
                    if (event.currentTarget.validity.valid)
                      event.currentTarget.blur();
                  }
                }}
              />
            )}
          </th>
        );
      })}
      <th scope="col">
        <span>Actions</span>
      </th>
    </tr>
  );
}
