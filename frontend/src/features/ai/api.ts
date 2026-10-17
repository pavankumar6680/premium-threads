import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';

export type AiStatus = { enabled: boolean; available: boolean; model: string; message: string };

export type AiExplanation = {
  status: 'OK' | 'UNAVAILABLE' | 'REJECTED';
  text: string | null;
  model: string;
  cached: boolean;
  message: string | null;
};

export function useAiStatus() {
  return useQuery({ queryKey: ['ai', 'status'], queryFn: () => api.get<AiStatus>('/ai/status'), staleTime: 30_000 });
}

export function useExplainRun(runId: string) {
  return useMutation({ mutationFn: () => api.post<AiExplanation>(`/recommendations/${runId}/explanation`) });
}
