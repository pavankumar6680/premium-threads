import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { AuthCard } from './components/AuthCard';
import { useCurrentUser, useRegister } from './hooks';
import { registerSchema } from './schemas';
import { applyServerErrors } from './serverErrors';

export function RegisterPage() {
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();
  const registerUser = useRegister();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '', confirmPassword: '' },
  });

  if (user && !registerUser.isSuccess) return <Navigate to="/" replace />;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await registerUser.mutateAsync(values);
      navigate('/account', { replace: true });
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ['fullName', 'email', 'password']));
    }
  });

  return (
    <AuthCard
      title="Create your account"
      subtitle="Save bundles, track orders and check out faster."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-brand-700 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
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
          autoComplete="new-password"
          hint="At least 8 characters, with a letter and a number."
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <Button type="submit" size="lg" className="w-full" isLoading={registerUser.isPending}>
          Create account
        </Button>
      </form>
    </AuthCard>
  );
}
