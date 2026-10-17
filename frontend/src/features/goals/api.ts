import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { useCurrentUser } from '../auth/hooks';
import type { Page } from '../catalog/types';

export type Quality = 'BUDGET' | 'BALANCED' | 'PREMIUM';
export type RequirementKind = 'REQUIRED' | 'OPTIONAL' | 'OWNED';

/** A category with live availability (prices cover in-stock products only). */
export type CategoryOption = {
  slug: string;
  name: string;
  departmentSlug: string;
  departmentName: string;
  inStockCount: number;
  minPrice: number | null;
  maxPrice: number | null;
  brands: string[];
};

export type TemplateSummary = {
  slug: string;
  title: string;
  tagline: string;
  defaultBudget: number;
  essentialsMinimum: number;
  categoryNames: string[];
};

export type TemplateDetail = {
  slug: string;
  title: string;
  tagline: string;
  description: string;
  defaultBudget: number;
  essentialsMinimum: number;
  usageOptions: { tag: string; label: string }[];
  items: { category: CategoryOption; required: boolean; selectedByDefault: boolean; quantity: number; note: string | null }[];
};

export type GoalAnalysis = {
  source: string;
  templateSlug: string | null;
  title: string;
  budget: number | null;
  quality: Quality;
  items: { categorySlug: string; kind: RequirementKind; quantity: number }[];
  usageTags: string[];
  preferredBrands: string[];
  matchedTerms: string[];
  clarifications: string[];
};

export type Goal = {
  id: string;
  title: string;
  template: { slug: string; title: string } | null;
  rawText: string | null;
  budget: number;
  quality: Quality;
  items: { category: CategoryOption; kind: RequirementKind; quantity: number }[];
  preferredBrands: string[];
  usageTags: string[];
  space: { widthCm: number | null; depthCm: number | null } | null;
  notes: string | null;
  createdAt: string;
  estimate: {
    essentialsMinimum: number;
    selectedMinimum: number;
    essentialsFit: boolean;
    selectionFits: boolean;
    unavailableCategories: string[];
  };
};

export type GoalSummary = {
  id: string;
  title: string;
  templateSlug: string | null;
  budget: number;
  quality: Quality;
  itemCount: number;
  createdAt: string;
};

export type CreateGoalInput = {
  templateSlug: string | null;
  title: string;
  rawText: string | null;
  budget: number;
  quality: Quality;
  items: { categorySlug: string; kind: RequirementKind; quantity: number }[];
  preferredBrands: string[];
  usageTags: string[];
  space: { widthCm: number | null; depthCm: number | null } | null;
  notes: string | null;
};

export const goalKeys = {
  templates: ['goals', 'templates'] as const,
  template: (slug: string) => ['goals', 'template', slug] as const,
  categories: ['goals', 'categories'] as const,
  goal: (id: string) => ['goals', 'goal', id] as const,
  mine: ['goals', 'mine'] as const,
};

export function useGoalTemplates() {
  return useQuery({ queryKey: goalKeys.templates, queryFn: () => api.get<TemplateSummary[]>('/goal-templates'), staleTime: 5 * 60_000 });
}

export function useGoalTemplate(slug: string | null) {
  return useQuery({
    queryKey: goalKeys.template(slug ?? ''),
    queryFn: () => api.get<TemplateDetail>(`/goal-templates/${encodeURIComponent(slug!)}`),
    enabled: !!slug,
    staleTime: 5 * 60_000,
  });
}

export function useGoalCategories() {
  return useQuery({ queryKey: goalKeys.categories, queryFn: () => api.get<CategoryOption[]>('/goal-categories'), staleTime: 5 * 60_000 });
}

export function useGoal(id: string | null) {
  return useQuery({
    queryKey: goalKeys.goal(id ?? ''),
    queryFn: () => api.get<Goal>(`/goals/${encodeURIComponent(id!)}`),
    enabled: !!id,
  });
}

export function useMyGoals() {
  const { data: user } = useCurrentUser();
  return useQuery({ queryKey: goalKeys.mine, queryFn: () => api.get<Page<GoalSummary>>('/me/goals?size=5'), enabled: !!user });
}

export function useAnalyzeGoal() {
  return useMutation({ mutationFn: (text: string) => api.post<GoalAnalysis>('/goals/analyze', { text }) });
}

export function useCreateGoal() {
  return useMutation({ mutationFn: (input: CreateGoalInput) => api.post<Goal>('/goals', input) });
}

export const qualityLabels: Record<Quality, { label: string; description: string }> = {
  BUDGET: { label: 'Budget', description: 'Essentials first, lowest total cost' },
  BALANCED: { label: 'Balanced', description: 'The best value for the money' },
  PREMIUM: { label: 'Premium', description: 'Top performance within your budget' },
};

export const kindLabels: Record<RequirementKind | 'SKIP', string> = {
  REQUIRED: 'Need it',
  OPTIONAL: 'Nice to have',
  OWNED: 'Already own',
  SKIP: 'Skip',
};

/** Product tags shown as usage options when a goal is not based on a template. */
export const generalUsageOptions = [
  { tag: 'ergonomic', label: 'Ergonomic comfort' },
  { tag: 'compact', label: 'Small space' },
  { tag: 'wireless', label: 'Wireless' },
  { tag: 'quiet', label: 'Quiet' },
  { tag: 'portable', label: 'Portable' },
  { tag: 'beginner', label: 'Beginner friendly' },
  { tag: 'pro', label: 'Professional grade' },
];

/** Categories where the available floor space matters. */
export const SPACE_SENSITIVE_CATEGORIES = ['desks', 'benches', 'cardio-machines', 'exercise-mats'];
