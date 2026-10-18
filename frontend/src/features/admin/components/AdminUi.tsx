import clsx from 'clsx';
import { useId, type ComponentProps, type ReactNode } from 'react';
import { ApiError } from '../../../lib/api';

/** Field-level messages from an API error: bean-validation fieldErrors plus the `field` of business errors. */
export function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  const errors: Record<string, string> = {};
  for (const fe of error.fieldErrors) errors[fe.field] = fe.message;
  const field = error.details.field;
  if (typeof field === 'string') errors[field] = error.message;
  return errors;
}

export function errorMessage(error: unknown): string | null {
  if (!error) return null;
  if (error instanceof ApiError) {
    return error.fieldErrors.length > 0 ? 'Please correct the highlighted fields.' : error.message;
  }
  return 'Something went wrong. Please try again.';
}

const control =
  'block w-full rounded-lg border bg-white px-3 text-sm shadow-xs focus:outline-none focus:ring-2 disabled:bg-slate-50';
const controlTone = (error?: string) =>
  error ? 'border-red-400 focus:border-red-500 focus:ring-red-200' : 'border-slate-300 focus:border-brand-500 focus:ring-brand-100';

function FieldShell({ id, label, error, hint, className, children }: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-red-600">{error}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1.5 text-sm text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

export function SelectField({ label, error, hint, className, children, ...props }: ComponentProps<'select'> & {
  label: string;
  error?: string;
  hint?: string;
}) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={className}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        className={clsx(control, 'h-11', controlTone(error))}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  );
}

export function TextAreaField({ label, error, hint, className, ...props }: ComponentProps<'textarea'> & {
  label: string;
  error?: string;
  hint?: string;
}) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={className}>
      <textarea
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        className={clsx(control, 'min-h-24 py-2', controlTone(error))}
        {...props}
      />
    </FieldShell>
  );
}

export function Checkbox({ label, className, ...props }: ComponentProps<'input'> & { label: string }) {
  return (
    <label className={clsx('inline-flex items-center gap-2 text-sm text-slate-700', className)}>
      <input type="checkbox" className="size-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" {...props} />
      {label}
    </label>
  );
}

/** On/off switch for a row (product active, rule active). */
export function Toggle({ checked, onChange, label, disabled }: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={clsx(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50',
        checked ? 'bg-emerald-500' : 'bg-slate-300',
      )}
    >
      <span className={clsx('inline-block size-5 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-5' : 'translate-x-0.5')} />
    </button>
  );
}

export function Card({ title, action, className, children }: {
  title?: string;
  action?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={clsx('min-w-0 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs', className)}>
      {(title || action) && (
        <div className="mb-4 flex items-center justify-between gap-3">
          {title && <h2 className="text-base font-semibold text-slate-900">{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-sm text-slate-600">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function Badge({ tone = 'slate', children }: { tone?: 'slate' | 'green' | 'amber' | 'red' | 'brand'; children: ReactNode }) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700',
    green: 'bg-emerald-100 text-emerald-800',
    amber: 'bg-amber-100 text-amber-800',
    red: 'bg-red-100 text-red-700',
    brand: 'bg-brand-100 text-brand-800',
  };
  return <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold', tones[tone])}>{children}</span>;
}

/** Horizontally scrollable table wrapper, so wide admin tables never widen the page on phones. */
export function TableWrap({ children }: { children: ReactNode }) {
  return (
    <div className="relative overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-xs">
      <table className="min-w-full divide-y divide-slate-200 text-sm">{children}</table>
    </div>
  );
}

export const th = 'px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 whitespace-nowrap';
export const td = 'px-4 py-3 align-middle text-slate-700';

/** Splits "a, b, c" into trimmed, non-empty, unique values. */
export function splitList(text: string): string[] {
  return [...new Set(text.split(',').map((v) => v.trim()).filter(Boolean))];
}
