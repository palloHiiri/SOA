import { type FormEvent, useState } from "react";

import type { TicketRequest, TicketType } from "../types/ticket";

interface TicketFiltersProps {
  onApply: (request: TicketRequest) => void;
}

export function TicketFilters({ onApply }: TicketFiltersProps) {
  const [id, setId] = useState("");
  const [name, setName] = useState("");

  const [venueId, setVenueId] = useState("");
  const [trainSetId, setTrainSetId] = useState("");

  const [carriageNumber, setCarriageNumber] = useState("");

  const [seatNumber, setSeatNumber] = useState("");

  const [basePrice, setBasePrice] = useState("");

  const [discount, setDiscount] = useState("");

  const [type, setType] = useState<TicketType | "">("");

  const [refundable, setRefundable] = useState<"" | "true" | "false">("");

  const [sortField, setSortField] = useState("id");

  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

  const [size, setSize] = useState(10);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const request: TicketRequest = {
      page: 1,
      size,
      sort: `${sortField},${sortDirection}`,
    };

    if (id) {
      request.id = Number(id);
    }

    if (name.trim()) {
      request.name = name.trim();
    }

    if (venueId) {
      request.venueId = Number(venueId);
    }

    if (trainSetId) {
      request.trainSetId = Number(trainSetId);
    }

    if (carriageNumber.trim()) {
      request.carriageNumber = carriageNumber.trim();
    }

    if (seatNumber.trim()) {
      request.seatNumber = seatNumber.trim();
    }

    if (basePrice) {
      request.basePrice = Number(basePrice);
    }

    if (discount) {
      request.discount = Number(discount);
    }

    if (type) {
      request.type = type;
    }

    if (refundable) {
      request.refundable = refundable === "true";
    }

    onApply(request);
  }

  function resetFilters() {
    setId("");
    setName("");

    setVenueId("");
    setTrainSetId("");

    setCarriageNumber("");
    setSeatNumber("");

    setBasePrice("");
    setDiscount("");

    setType("");
    setRefundable("");

    setSortField("id");
    setSortDirection("asc");

    setSize(10);

    onApply({
      page: 1,
      size: 10,
      sort: "id,asc",
    });
  }

  return (
    <form className="ticket-filters" onSubmit={handleSubmit}>
      <div className="filters-title">
        <div>
          <strong>Search & filters</strong>

          <span>Filter available tickets</span>
        </div>
      </div>

      <div className="filters-grid">
        <label>
          <span>ID</span>

          <input
            type="number"
            min="1"
            value={id}
            onChange={(e) => setId(e.target.value)}
          />
        </label>

        <label>
          <span>Name</span>

          <input
            value={name}
            placeholder="Test Ticket"
            onChange={(e) => setName(e.target.value)}
          />
        </label>

        <label>
          <span>Venue ID</span>

          <input
            type="number"
            min="1"
            value={venueId}
            onChange={(e) => setVenueId(e.target.value)}
          />
        </label>

        <label>
          <span>Train set</span>

          <input
            type="number"
            min="1"
            value={trainSetId}
            onChange={(e) => setTrainSetId(e.target.value)}
          />
        </label>

        <label>
          <span>Carriage</span>

          <input
            value={carriageNumber}
            placeholder="01"
            onChange={(e) => setCarriageNumber(e.target.value)}
          />
        </label>

        <label>
          <span>Seat</span>

          <input
            value={seatNumber}
            placeholder="1A"
            onChange={(e) => setSeatNumber(e.target.value)}
          />
        </label>

        <label>
          <span>Price</span>

          <input
            type="number"
            min="0"
            value={basePrice}
            onChange={(e) => setBasePrice(e.target.value)}
          />
        </label>

        <label>
          <span>Discount</span>

          <input
            type="number"
            min="1"
            max="100"
            value={discount}
            onChange={(e) => setDiscount(e.target.value)}
          />
        </label>

        <label>
          <span>Type</span>

          <select
            value={type}
            onChange={(e) => {
              const value = e.target.value;

              if (
                value === "" ||
                value === "VIP" ||
                value === "USUAL" ||
                value === "CHEAP"
              ) {
                setType(value);
              }
            }}
          >
            <option value="">Any</option>

            <option value="VIP">VIP</option>

            <option value="USUAL">USUAL</option>

            <option value="CHEAP">CHEAP</option>
          </select>
        </label>

        <label>
          <span>Refundable</span>

          <select
            value={refundable}
            onChange={(e) => {
              const value = e.target.value;

              if (value === "" || value === "true" || value === "false") {
                setRefundable(value);
              }
            }}
          >
            <option value="">Any</option>

            <option value="true">Yes</option>

            <option value="false">No</option>
          </select>
        </label>
      </div>

      <div className="filter-sort-row">
        <label>
          <span>Sort by</span>

          <select
            value={sortField}
            onChange={(e) => setSortField(e.target.value)}
          >
            <option value="id">ID</option>

            <option value="name">Name</option>

            <option value="creationDate">Creation date</option>

            <option value="basePrice">Price</option>

            <option value="discount">Discount</option>

            <option value="carriageNumber">Carriage</option>

            <option value="seatNumber">Seat</option>
          </select>
        </label>

        <label>
          <span>Direction</span>

          <select
            value={sortDirection}
            onChange={(e) => {
              if (e.target.value === "asc" || e.target.value === "desc") {
                setSortDirection(e.target.value);
              }
            }}
          >
            <option value="asc">Ascending</option>

            <option value="desc">Descending</option>
          </select>
        </label>

        <label>
          <span>Per page</span>

          <select
            value={size}
            onChange={(e) => setSize(Number(e.target.value))}
          >
            <option value={5}>5</option>

            <option value={10}>10</option>

            <option value={20}>20</option>

            <option value={50}>50</option>
          </select>
        </label>
      </div>

      <div className="filters-actions">
        <button type="button" className="cancel-button" onClick={resetFilters}>
          Reset
        </button>

        <button type="submit" className="submit-booking-button">
          Apply
        </button>
      </div>
    </form>
  );
}
