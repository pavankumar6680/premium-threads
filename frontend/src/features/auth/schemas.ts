import { z } from 'zod';

// Mirrors the backend Bean Validation rules in AuthDtos so users get instant feedback;
// the server remains the source of truth and its field errors are shown too.

const email = z.string().trim().min(1, 'Email is required').pipe(z.email('Enter a valid email address'));

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Password is required'),
});

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Name is required').max(120, 'Name is too long'),
    email,
    password: z
      .string()
      .min(8, 'Use at least 8 characters')
      .max(72, 'Use at most 72 characters')
      .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, 'Include at least one letter and one number'),
    confirmPassword: z.string().min(1, 'Confirm your password'),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  });

export type LoginInput = z.infer<typeof loginSchema>;
export type RegisterInput = z.infer<typeof registerSchema>;
