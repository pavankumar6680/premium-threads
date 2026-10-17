import { Minus, Plus } from 'lucide-react';

export function QuantityStepper({ value, min = 1, max, onChange, disabled, label = 'Quantity' }: {
  value: number;
  min?: number;
  max: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  label?: string;
}) {
  const button = 'flex size-9 items-center justify-center text-slate-600 hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300';
  return (
    <div className="inline-flex items-center rounded-lg border border-slate-300 bg-white" role="group" aria-label={label}>
      <button type="button" className={`${button} rounded-l-lg`} onClick={() => onChange(value - 1)}
        disabled={disabled || value <= min} aria-label="Decrease quantity">
        <Minus className="size-4" aria-hidden="true" />
      </button>
      <span className="w-10 text-center text-sm font-semibold tabular-nums" aria-live="polite">
        {value}
      </span>
      <button type="button" className={`${button} rounded-r-lg`} onClick={() => onChange(value + 1)}
        disabled={disabled || value >= max} aria-label="Increase quantity">
        <Plus className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}
