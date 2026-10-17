import clsx from 'clsx';
import { useId, type ComponentProps } from 'react';

type TextFieldProps = ComponentProps<'input'> & {
  label: string;
  error?: string;
  hint?: string;
};

export function TextField({ label, error, hint, className, id, ...props }: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;

  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={clsx(
          'block h-11 w-full rounded-lg border bg-white px-3 text-sm shadow-xs transition-colors placeholder:text-slate-400 focus:outline-none focus:ring-2',
          error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
            : 'border-slate-300 focus:border-brand-500 focus:ring-brand-100',
        )}
        {...props}
      />
      {error ? (
        <p id={`${inputId}-error`} className="mt-1.5 text-sm text-red-600">
          {error}
        </p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="mt-1.5 text-sm text-slate-500">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
