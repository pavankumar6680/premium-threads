import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { safeRedirect } from '../../lib/safeRedirect';
import { AuthCard } from './components/AuthCard';
import { useCurrentUser, useLogin } from './hooks';
import { loginSchema } from './schemas';
import { applyServerErrors } from './serverErrors';

export function LoginPage() {
  const [searchParams] = useSearchParams();
  const redirectTo = safeRedirect(searchParams.get('redirect'));
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();
  const login = useLogin();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } });

  if (user && !login.isSuccess) return <Navigate to={redirectTo} replace />;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login.mutateAsync(values);
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ['email', 'password']));
    }
  });

  return (
    <AuthCard
      title="Welcome back"
      subtitle="Sign in to continue building your bundles."
      footer={
        <>
          New to Premium Threads?{' '}
          <Link to="/register" className="font-semibold text-brand-700 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" size="lg" className="w-full" isLoading={login.isPending}>
          Sign in
        </Button>
      </form>
    </AuthCard>
  );
}
