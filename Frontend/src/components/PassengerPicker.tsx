import { useRef } from "react";
import type { Passenger } from "../types/passenger";

function passengerLabel(passenger: Passenger) {
  return `${[passenger.firstName, passenger.middleName, passenger.lastName].filter(Boolean).join(" ")} — ${passenger.documentSeriesNumber} ${passenger.documentNumber}`;
}

export function PassengerPicker({
  passengers,
  value,
  onChange,
}: {
  passengers: Passenger[];
  value: string;
  onChange: (value: string) => void;
}) {
  const disclosure = useRef<HTMLDetailsElement>(null);
  const selected = passengers.find((passenger) => passenger.id === value);
  function close() {
    if (disclosure.current) {
      disclosure.current.open = false;
      disclosure.current.querySelector("summary")?.focus();
    }
  }
  return (
    <details
      ref={disclosure}
      className="passenger-picker"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          close();
        }
      }}
    >
      <summary
        aria-label={`Passenger: ${selected ? passengerLabel(selected) : "Select passenger"}`}
      >
        {selected ? passengerLabel(selected) : "Select passenger"}
      </summary>
      <ul className="passenger-picker-options" aria-label="Passengers">
        {passengers.map((passenger) => (
          <li key={passenger.id}>
            <button
              type="button"
              aria-pressed={passenger.id === value}
              onClick={() => {
                onChange(passenger.id);
                close();
              }}
            >
              {passengerLabel(passenger)}
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}
