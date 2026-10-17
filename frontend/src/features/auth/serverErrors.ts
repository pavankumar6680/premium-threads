import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '../../lib/api';

/**
 * Copies server-side field validation errors onto the form. Returns the message to show in a
 * form-level alert, or null if every error was attached to a field.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): string | null {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.';

  const unmatched = error.fieldErrors.filter((fe) => {
    const field = knownFields.find((f) => f === fe.field);
    if (field) setError(field, { type: 'server', message: fe.message });
    return !field;
  });

  if (error.fieldErrors.length > 0 && unmatched.length === 0) return null;
  return error.message;
}
