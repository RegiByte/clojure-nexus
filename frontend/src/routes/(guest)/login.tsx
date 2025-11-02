import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { authenticatedUserQueryOptions, useAuth } from '@/lib/auth'
import { ApiRequestError } from '@/lib/api'
import { cn } from '@/lib/utils'
import {
  createFileRoute,
  Link,
  Navigate,
  redirect,
  useNavigate,
} from '@tanstack/react-router'
import { fallback, zodValidator } from '@tanstack/zod-adapter'
import { AlertCircleIcon } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { z } from 'zod'

export const Route = createFileRoute('/(guest)/login')({
  component: LoginPage,
  validateSearch: zodValidator(
    z.object({
      redirect: fallback(z.string().optional(), ''),
    }),
  ),
})

function LoginPage() {
  const navigate = useNavigate()
  const { login, isLoginPending, isAuthenticated } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const search = Route.useSearch()
  const redirectTarget = search.redirect ?? '/app'

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError(null)

    try {
      const formData = new FormData(e.currentTarget)
      const email = formData.get('email') as string
      const password = formData.get('password') as string
      await login({ email, password })
      // Redirect to app on successful login
      navigate({ to: redirectTarget })
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('An unexpected error occurred. Please try again.')
      }
    }
  }
  if (isAuthenticated) {
    return <Navigate to={redirectTarget} />
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-stone-200">
      <div className={cn('flex flex-col gap-6 max-w-md w-full')}>
        <Card>
          <CardHeader>
            <CardTitle>Login to your account</CardTitle>
            <CardDescription>
              Enter your email below to login to your account
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit}>
              {error && (
                <Alert variant="destructive" className="mb-2">
                  <AlertDescription>
                    <span>
                      <AlertCircleIcon className="size-4 inline-block" />{' '}
                      {error}
                    </span>
                  </AlertDescription>
                </Alert>
              )}
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="email">Email</FieldLabel>
                  <Input
                    id="email"
                    type="email"
                    name="email"
                    placeholder="m@example.com"
                    required
                  />
                </Field>
                <Field>
                  <div className="flex items-center">
                    <FieldLabel htmlFor="password">Password</FieldLabel>
                    <a
                      href="#"
                      className="ml-auto inline-block text-sm underline-offset-4 hover:underline"
                    >
                      Forgot your password?
                    </a>
                  </div>
                  <Input
                    id="password"
                    type="password"
                    name="password"
                    required
                  />
                </Field>
                <Field>
                  <Button type="submit" disabled={isLoginPending}>
                    {isLoginPending ? 'Logging in...' : 'Login'}
                  </Button>
                  <FieldDescription className="text-center">
                    Don&apos;t have an account?{' '}
                    <Link
                      to="/signup"
                      search={{ redirect: redirectTarget }}
                      className="underline"
                    >
                      Sign up
                    </Link>
                  </FieldDescription>
                </Field>
              </FieldGroup>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
