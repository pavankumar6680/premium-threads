import clsx from 'clsx';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { Button } from '../../../components/ui/Button';
import { formatAttributeValue } from '../attributes';
import type { CategoryNode, Facets } from '../types';

type Props = {
  params: URLSearchParams;
  categories: CategoryNode[];
  facets: Facets | undefined;
  onToggle: (key: string, value: string) => void;
  onSet: (updates: Record<string, string | null>) => void;
};

export function FilterPanel({ params, categories, facets, onToggle, onSet }: Props) {
  const current = params.get('category');
  const activeDepartment = categories.find((d) => d.slug === current || d.children.some((c) => c.slug === current));

  return (
    <div className="space-y-6">
      <FilterSection title="Categories">
        <ul className="space-y-1 text-sm">
          <li>
            <CategoryLink params={params} slug={null} active={!current} label="All products" />
          </li>
          {categories.map((dept) => (
            <li key={dept.slug}>
              <CategoryLink params={params} slug={dept.slug} active={current === dept.slug} label={dept.name} count={dept.productCount} />
              {activeDepartment?.slug === dept.slug && (
                <ul className="ml-3 mt-1 space-y-1 border-l border-slate-200 pl-3">
                  {dept.children.map((leaf) => (
                    <li key={leaf.slug}>
                      <CategoryLink params={params} slug={leaf.slug} active={current === leaf.slug} label={leaf.name} count={leaf.productCount} />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      </FilterSection>

      {facets && facets.brands.length > 1 && (
        <FilterSection title="Brand">
          <CheckboxList
            name="brand"
            options={facets.brands.map((b) => ({ value: b.value, label: b.value, count: b.count }))}
            selected={params.getAll('brand')}
            onToggle={(v) => onToggle('brand', v)}
          />
        </FilterSection>
      )}

      <FilterSection title="Price">
        <PriceFilter
          key={`${params.get('minPrice')}-${params.get('maxPrice')}`}
          min={params.get('minPrice') ?? ''}
          max={params.get('maxPrice') ?? ''}
          placeholderMin={facets?.minPrice}
          placeholderMax={facets?.maxPrice}
          onApply={(minPrice, maxPrice) => onSet({ minPrice: minPrice || null, maxPrice: maxPrice || null })}
        />
      </FilterSection>

      <FilterSection title="Customer rating">
        <div className="space-y-1.5 text-sm">
          {[
            { value: '4.5', label: '4.5 stars & up' },
            { value: '4', label: '4 stars & up' },
            { value: '', label: 'Any rating' },
          ].map((option) => (
            <label key={option.label} className="flex cursor-pointer items-center gap-2">
              <input
                type="radio"
                name="minRating"
                className="size-4 accent-brand-600"
                checked={(params.get('minRating') ?? '') === option.value}
                onChange={() => onSet({ minRating: option.value || null })}
              />
              {option.label}
            </label>
          ))}
        </div>
      </FilterSection>

      {facets?.attributes.map((attribute) => (
        <FilterSection key={attribute.key} title={attribute.label}>
          <CheckboxList
            name={`attr.${attribute.key}`}
            options={attribute.values.map((v) => ({
              value: v.value,
              label: formatAttributeValue(attribute.type, attribute.unit, v.value),
              count: v.count,
            }))}
            selected={params.getAll(`attr.${attribute.key}`)}
            onToggle={(v) => onToggle(`attr.${attribute.key}`, v)}
          />
        </FilterSection>
      ))}
    </div>
  );
}

function FilterSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-semibold text-slate-900">{title}</legend>
      {children}
    </fieldset>
  );
}

function CategoryLink({ params, slug, label, count, active }: {
  params: URLSearchParams;
  slug: string | null;
  label: string;
  count?: number;
  active: boolean;
}) {
  // Changing category keeps the search text but drops category-specific refinements.
  const next = new URLSearchParams();
  const q = params.get('q');
  if (q) next.set('q', q);
  if (slug) next.set('category', slug);
  return (
    <Link
      to={`/shop?${next.toString()}`}
      aria-current={active ? 'page' : undefined}
      className={clsx(
        'flex items-center justify-between rounded-md px-2 py-1 hover:bg-slate-100',
        active ? 'bg-brand-50 font-semibold text-brand-800' : 'text-slate-700',
      )}
    >
      <span>{label}</span>
      {count !== undefined && <span className="text-xs text-slate-400">{count}</span>}
    </Link>
  );
}

const VISIBLE_OPTIONS = 6;

function CheckboxList({ name, options, selected, onToggle }: {
  name: string;
  options: { value: string; label: string; count: number }[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const visible = expanded ? options : options.slice(0, VISIBLE_OPTIONS);
  return (
    <div className="space-y-1.5 text-sm">
      {visible.map((option) => (
        <label key={option.value} className="flex cursor-pointer items-center gap-2">
          <input
            type="checkbox"
            name={name}
            value={option.value}
            className="size-4 rounded accent-brand-600"
            checked={selected.includes(option.value)}
            onChange={() => onToggle(option.value)}
          />
          <span className="flex-1">{option.label}</span>
          <span className="text-xs text-slate-400">{option.count}</span>
        </label>
      ))}
      {options.length > VISIBLE_OPTIONS && (
        <button type="button" className="text-xs font-semibold text-brand-700 hover:underline" onClick={() => setExpanded((e) => !e)}>
          {expanded ? 'Show less' : `Show all ${options.length}`}
        </button>
      )}
    </div>
  );
}

function PriceFilter({ min, max, placeholderMin, placeholderMax, onApply }: {
  min: string;
  max: string;
  placeholderMin?: number | null;
  placeholderMax?: number | null;
  onApply: (min: string, max: string) => void;
}) {
  const [minValue, setMinValue] = useState(min);
  const [maxValue, setMaxValue] = useState(max);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    onApply(minValue.trim(), maxValue.trim());
  };
  const input = 'h-9 w-full rounded-lg border border-slate-300 px-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100';
  return (
    <form onSubmit={submit} className="flex items-center gap-2">
      <input aria-label="Minimum price" inputMode="decimal" type="number" min={0} className={input}
        placeholder={placeholderMin != null ? `$${Math.floor(placeholderMin)}` : 'Min'}
        value={minValue} onChange={(e) => setMinValue(e.target.value)} />
      <span className="text-slate-400">–</span>
      <input aria-label="Maximum price" inputMode="decimal" type="number" min={0} className={input}
        placeholder={placeholderMax != null ? `$${Math.ceil(placeholderMax)}` : 'Max'}
        value={maxValue} onChange={(e) => setMaxValue(e.target.value)} />
      <Button type="submit" variant="secondary" size="sm">Go</Button>
    </form>
  );
}
