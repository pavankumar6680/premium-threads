import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from './api';
import type { Role } from './types';

export const authKeys = {
  me: ['auth', 'me'] as const,
};

export function useCurrentUser() {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: authApi.me,
    staleTime: 5 * 60_000,
  });
}

export function useHasRole(role: Role): boolean {
  const { data: user } = useCurrentUser();
  return !!user?.roles.includes(role);
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (user) => queryClient.setQueryData(authKeys.me, user),
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.register,
    onSuccess: (user) => queryClient.setQueryData(authKeys.me, user),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      // Drop every cached per-user query (cart, orders, …) and mark the session as signed out.
      queryClient.clear();
      queryClient.setQueryData(authKeys.me, null);
    },
  });
}
