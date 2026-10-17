export type Role = 'CUSTOMER' | 'ADMIN';

export type User = {
  id: number;
  email: string;
  fullName: string;
  roles: Role[];
};
