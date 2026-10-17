import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { formatMoney } from '../../lib/format';
import { categoryVisual } from '../catalog/categoryVisuals';
import type { TemplateSummary } from './api';

// Each goal borrows the visual identity of its main department.
const goalDepartments: Record<string, string> = {
  'home-office': 'home-office',
  'gaming-setup': 'computers',
  'home-gym': 'fitness',
  'photo-studio': 'photography',
  'study-space': 'home-office',
  'podcast-studio': 'audio',
  'smart-home': 'smart-home',
};

export function TemplateCard({ template }: { template: TemplateSummary }) {
  const { icon: Icon, tile } = categoryVisual(goalDepartments[template.slug]);
  return (
    <Link
      to={`/goals/new?template=${template.slug}`}
      className="group flex flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-xs transition-shadow hover:shadow-md"
    >
      <span className={`flex size-12 items-center justify-center rounded-xl bg-gradient-to-br ${tile}`}>
        <Icon className="size-6" aria-hidden="true" />
      </span>
      <h3 className="mt-4 text-lg font-semibold group-hover:text-brand-700">{template.title}</h3>
      <p className="mt-1 text-sm text-slate-600">{template.tagline}</p>
      <p className="mt-3 line-clamp-2 text-xs text-slate-500">{template.categoryNames.join(' · ')}</p>
      <div className="mt-auto flex items-end justify-between pt-4">
        <p className="text-sm">
          <span className="text-slate-500">Complete setups from </span>
          <span className="font-bold text-slate-900">{formatMoney(template.essentialsMinimum)}</span>
        </p>
        <ArrowRight className="size-5 text-brand-600 transition-transform group-hover:translate-x-1" aria-hidden="true" />
      </div>
    </Link>
  );
}
