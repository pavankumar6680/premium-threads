import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

/** `page` is zero-based (as in the API); the UI shows one-based page numbers. */
export function Pagination({ page, totalPages, onChange }: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav className="mt-8 flex items-center justify-center gap-3" aria-label="Pagination">
      <Button variant="secondary" size="sm" onClick={() => onChange(page - 1)} disabled={page <= 0}>
        <ChevronLeft className="size-4" aria-hidden="true" /> Previous
      </Button>
      <span className="text-sm text-slate-600">
        Page <span className="font-semibold text-slate-900">{page + 1}</span> of {totalPages}
      </span>
      <Button variant="secondary" size="sm" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}>
        Next <ChevronRight className="size-4" aria-hidden="true" />
      </Button>
    </nav>
  );
}
