import clsx from 'clsx';
import { ImagePlus, Plus, Star, Trash2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { useToast } from '../../components/ui/Toast';
import {
  useDeleteImage,
  useImagePrimary,
  useProductImages,
  useSetVariantStock,
  useUploadImage,
  type AdminVariant,
  type ColorFamily,
} from './api';
import { Badge, Card, errorMessage, TableWrap, td, th } from './components/AdminUi';

/** One editable size × color row; `id` is set for variants that already exist. */
export type VariantRow = {
  key: string;
  id?: number;
  sku?: string;
  size: string;
  color: string;
  colorHex: string;
  colorFamily: ColorFamily;
  active: boolean;
  /** initial stock for new rows; existing rows change stock through the stock endpoint */
  stock: string;
  currentStock?: number;
};

let rowKey = 0;
const nextKey = () => `row-${++rowKey}`;

export function rowsOf(variants: AdminVariant[]): VariantRow[] {
  return variants.map((v) => ({
    key: nextKey(),
    id: v.id,
    sku: v.sku,
    size: v.size,
    color: v.color,
    colorHex: v.colorHex ?? '#CCCCCC',
    colorFamily: v.colorFamily,
    active: v.active,
    stock: String(v.stock),
    currentStock: v.stock,
  }));
}

export function newRow(size = '', color = '', colorHex = '#CCCCCC', colorFamily: ColorFamily = 'NEUTRAL'): VariantRow {
  return { key: nextKey(), size, color, colorHex, colorFamily, active: true, stock: '0' };
}

const SIZE_PRESETS: Record<string, string[]> = {
  ALPHA: ['XS', 'S', 'M', 'L', 'XL', 'XXL'],
  WAIST: ['28', '30', '32', '34', '36', '38'],
  ONE_SIZE: ['One size'],
};

/**
 * Sizes × colors with per-variant stock. Rows of saved variants keep their SKU; removing a saved row
 * deactivates it instead (orders reference it), so only new rows can be deleted.
 */
export function VariantsEditor({ rows, onChange, sizeScale }: {
  rows: VariantRow[];
  onChange: (rows: VariantRow[]) => void;
  sizeScale: string | null;
}) {
  const setStock = useSetVariantStock();
  const toast = useToast();
  const update = (key: string, patch: Partial<VariantRow>) => onChange(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const [color, setColor] = useState({ name: '', hex: '#1F2A44', family: 'NEUTRAL' as ColorFamily });
  const sizes = SIZE_PRESETS[sizeScale ?? ''] ?? [];

  const addColorway = () => {
    const name = color.name.trim();
    if (!name) return;
    const missing = (sizes.length ? sizes : ['One size']).filter((s) => !rows.some((r) => r.size === s && r.color.toLowerCase() === name.toLowerCase()));
    onChange([...rows, ...missing.map((s) => newRow(s, name, color.hex, color.family))]);
    setColor((c) => ({ ...c, name: '' }));
  };

  return (
    <Card title="Sizes, colors and stock">
      <p className="mb-3 text-sm text-slate-600">
        Every size × color is its own variant with its own stock. Saved variants keep their SKU; switch them off instead of deleting.
      </p>
      <div className="mb-4 flex flex-wrap items-end gap-2 rounded-xl bg-slate-50 p-3">
        <label className="text-xs font-medium text-slate-600">
          Add a colorway
          <input
            value={color.name}
            onChange={(e) => setColor((c) => ({ ...c, name: e.target.value }))}
            placeholder="e.g. Olive"
            className="mt-1 block h-9 w-36 rounded-lg border border-slate-300 px-2 text-sm"
          />
        </label>
        <label className="text-xs font-medium text-slate-600">
          Swatch
          <input type="color" value={color.hex} onChange={(e) => setColor((c) => ({ ...c, hex: e.target.value }))} className="mt-1 block h-9 w-12 rounded-lg border border-slate-300" aria-label="Swatch color" />
        </label>
        <label className="text-xs font-medium text-slate-600">
          Family
          <FamilySelect value={color.family} onChange={(family) => setColor((c) => ({ ...c, family }))} />
        </label>
        <Button type="button" variant="secondary" size="sm" onClick={addColorway} disabled={!color.name.trim()}>
          <Plus className="size-4" aria-hidden="true" /> Add {sizes.length > 1 ? `in ${sizes.length} sizes` : ''}
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={() => onChange([...rows, newRow(sizes[0] ?? 'One size')])}>
          Add a single row
        </Button>
      </div>
      {rows.length === 0 ? (
        <p className="text-sm text-slate-500">No variants yet: the product is sold as a single “One size” option.</p>
      ) : (
        <TableWrap>
          <table className="min-w-full divide-y divide-slate-200 text-sm" aria-label="Variants">
            <thead className="bg-slate-50">
              <tr>
                <th className={th}>Size</th>
                <th className={th}>Color</th>
                <th className={th}>Family</th>
                <th className={th}>Stock</th>
                <th className={th}>On sale</th>
                <th className={th}><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.key} className={clsx(!r.active && 'bg-slate-50 text-slate-400')}>
                  <td className={td}>
                    <input aria-label="Size" value={r.size} onChange={(e) => update(r.key, { size: e.target.value })} className="h-9 w-20 rounded-lg border border-slate-300 px-2" />
                    {r.sku && <p className="mt-0.5 font-mono text-[11px] text-slate-400">{r.sku}</p>}
                  </td>
                  <td className={td}>
                    <span className="flex items-center gap-1.5">
                      <input type="color" aria-label={`Swatch for ${r.color || 'new color'}`} value={r.colorHex} onChange={(e) => update(r.key, { colorHex: e.target.value })} className="h-9 w-10 rounded-lg border border-slate-300" />
                      <input aria-label="Color" value={r.color} onChange={(e) => update(r.key, { color: e.target.value })} className="h-9 w-28 rounded-lg border border-slate-300 px-2" />
                    </span>
                  </td>
                  <td className={td}><FamilySelect value={r.colorFamily} onChange={(colorFamily) => update(r.key, { colorFamily })} /></td>
                  <td className={td}>
                    <span className="flex items-center gap-1.5">
                      <input
                        aria-label={`Stock of ${r.size} ${r.color}`}
                        type="number"
                        min={0}
                        value={r.stock}
                        onChange={(e) => update(r.key, { stock: e.target.value })}
                        className="h-9 w-20 rounded-lg border border-slate-300 px-2"
                      />
                      {r.id !== undefined && Number(r.stock) !== r.currentStock && (
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          isLoading={setStock.isPending && setStock.variables?.variantId === r.id}
                          onClick={() =>
                            setStock.mutate(
                              { variantId: r.id!, quantity: Math.max(0, Math.floor(Number(r.stock) || 0)) },
                              {
                                onSuccess: () => {
                                  update(r.key, { currentStock: Number(r.stock) });
                                  toast.show('Stock updated.');
                                },
                                onError: (e) => toast.show(errorMessage(e) ?? 'Could not update stock', 'error'),
                              },
                            )
                          }
                        >
                          Set
                        </Button>
                      )}
                    </span>
                  </td>
                  <td className={td}>
                    <input type="checkbox" aria-label={`${r.size} ${r.color} on sale`} checked={r.active} onChange={(e) => update(r.key, { active: e.target.checked })} className="size-4 accent-brand-600" />
                  </td>
                  <td className={td}>
                    {r.id === undefined && (
                      <button type="button" onClick={() => onChange(rows.filter((x) => x.key !== r.key))} className="rounded p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600" aria-label={`Remove ${r.size} ${r.color}`}>
                        <Trash2 className="size-4" aria-hidden="true" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}
    </Card>
  );
}

function FamilySelect({ value, onChange }: { value: ColorFamily; onChange: (family: ColorFamily) => void }) {
  return (
    <select aria-label="Color family" value={value} onChange={(e) => onChange(e.target.value as ColorFamily)} className="mt-1 block h-9 rounded-lg border border-slate-300 bg-white px-2 text-sm text-slate-900">
      <option value="NEUTRAL">Neutral</option>
      <option value="ACCENT">Accent</option>
      <option value="DENIM">Denim</option>
      <option value="METAL">Metal</option>
    </select>
  );
}

/** Product photos: upload (JPEG, PNG or WebP up to 5 MB), choose the main one, remove. */
export function ImagesCard({ productId, colors }: { productId: number; colors: string[] }) {
  const images = useProductImages(productId);
  const upload = useUploadImage();
  const primary = useImagePrimary();
  const remove = useDeleteImage();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [color, setColor] = useState('');

  const onFile = (file: File | undefined) => {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      toast.show('Images can be at most 5 MB.', 'error');
      return;
    }
    upload.mutate(
      { productId, file, color },
      {
        onSuccess: () => toast.show('Image uploaded.'),
        onError: (e) => toast.show(errorMessage(e) ?? 'Upload failed', 'error'),
        onSettled: () => {
          if (input.current) input.current.value = '';
        },
      },
    );
  };

  return (
    <Card
      title="Images"
      action={
        <div className="flex items-center gap-2">
          {colors.length > 1 && (
            <select aria-label="Image color" value={color} onChange={(e) => setColor(e.target.value)} className="h-9 rounded-lg border border-slate-300 bg-white px-2 text-sm">
              <option value="">All colors</option>
              {colors.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          )}
          <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" id="image-upload" onChange={(e) => onFile(e.target.files?.[0])} />
          <label htmlFor="image-upload" className={clsx('inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-lg bg-white px-3 text-sm font-semibold text-slate-800 ring-1 ring-slate-300 hover:bg-slate-50', upload.isPending && 'pointer-events-none opacity-60')}>
            <ImagePlus className="size-4" aria-hidden="true" /> {upload.isPending ? 'Uploading…' : 'Upload image'}
          </label>
        </div>
      }
    >
      <p className="mb-3 text-sm text-slate-600">
        Use photos you have the rights to. Without images, the store shows a generated illustration in the variant’s color.
      </p>
      {images.data?.length === 0 && <p className="text-sm text-slate-500">No images yet.</p>}
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {images.data?.map((img, i) => (
          <li key={img.id} className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <img src={img.url} alt={img.altText ?? ''} className="aspect-[5/6] w-full object-cover" />
            <div className="flex items-center justify-between gap-1 p-2">
              <span className="flex flex-wrap gap-1">
                {i === 0 && <Badge tone="brand">Main</Badge>}
                {img.color && <Badge>{img.color}</Badge>}
              </span>
              <span className="flex">
                {i > 0 && (
                  <button type="button" onClick={() => primary.mutate({ productId, imageId: img.id })} className="rounded p-1.5 text-slate-500 hover:bg-slate-100" aria-label="Make main image">
                    <Star className="size-4" aria-hidden="true" />
                  </button>
                )}
                <button type="button" onClick={() => window.confirm('Remove this image?') && remove.mutate({ productId, imageId: img.id })} className="rounded p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600" aria-label="Remove image">
                  <Trash2 className="size-4" aria-hidden="true" />
                </button>
              </span>
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
