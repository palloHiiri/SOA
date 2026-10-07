// Accept partial input while typing, with either decimal separator.
export function isPriceInput(value: string): boolean {
  return /^\d{0,10}(?:[.,]\d{0,2})?$/.test(value);
}

export function parsePrice(value: string): number {
  return /^\d{1,10}(?:[.,]\d{0,2})?$/.test(value)
    ? Number(value.replace(",", "."))
    : Number.NaN;
}
