import { api, ApiError } from '../../lib/api';
import type { LoginInput, RegisterInput } from './schemas';
import type { User } from './types';

export const authApi = {
  /** Returns null when not signed in, rather than treating 401 as an error. */
  async me(): Promise<User | null> {
    try {
      return await api.get<User>('/auth/me');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) return null;
      throw error;
    }
  },
  login: (input: LoginInput) => api.post<User>('/auth/login', input),
  register: ({ fullName, email, password }: RegisterInput) =>
    api.post<User>('/auth/register', { fullName, email, password }),
  logout: () => api.post<void>('/auth/logout'),
};
