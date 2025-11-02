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
import { authenticatedUserQueryOptions, useAuth } from '@/contexts/auth'
import { authApi, ApiRequestError } from '@/lib/api'
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

export const Route = createFileRoute('/(guest)/signup')({
  component: SignupPage,
  validateSearch: zodValidator(
    z.object({
      redirect: fallback(z.string().optional(), ''),
    }),
  ),
})

function SignupPage() {
  const navigate = useNavigate()
  const { login, isAuthenticated } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const search = Route.useSearch()
  const redirectTarget = search.redirect ?? '/app'

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const formData = new FormData(e.currentTarget)
      const email = formData.get('email') as string
      const password = formData.get('password') as string
      const passwordConfirmation = formData.get(
        'password-confirmation',
      ) as string
      const firstName = formData.get('first-name') as string
      const lastName = formData.get('last-name') as string
      const middleName = formData.get('middle-name') as string

      // Validate password confirmation
      if (password !== passwordConfirmation) {
        setError('Passwords do not match')
        setIsSubmitting(false)
        return
      }

      // Validate password length
      if (password.length < 8) {
        setError('Password must be at least 8 characters long')
        setIsSubmitting(false)
        return
      }

      // Register the user
      await authApi.register({
        email,
        password,
        firstName,
        lastName,
        middleName,
      })

      // Automatically log in after successful registration
      await login({ email, password })

      // Redirect to app on successful registration and login
      navigate({ to: redirectTarget })
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('An unexpected error occurred. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
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
            <CardTitle>Create an account</CardTitle>
            <CardDescription>
              Enter your information below to create your account
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
                <div className="grid grid-rows-3 gap-4">
                  <Field>
                    <FieldLabel htmlFor="first-name">First Name</FieldLabel>
                    <Input
                      id="first-name"
                      type="text"
                      name="first-name"
                      placeholder="John"
                      required
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="middle-name">Middle Name</FieldLabel>
                    <Input
                      id="middle-name"
                      type="text"
                      name="middle-name"
                      placeholder="Marie"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="last-name">Last Name</FieldLabel>
                    <Input
                      id="last-name"
                      type="text"
                      name="last-name"
                      placeholder="Doe"
                      required
                    />
                  </Field>
                </div>
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
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <Input
                    id="password"
                    type="password"
                    name="password"
                    placeholder="Minimum 8 characters"
                    required
                    minLength={8}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="password-confirmation">
                    Confirm Password
                  </FieldLabel>
                  <Input
                    id="password-confirmation"
                    type="password"
                    name="password-confirmation"
                    placeholder="Re-enter your password"
                    required
                    minLength={8}
                  />
                </Field>
                <Field>
                  <Button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? 'Creating account...' : 'Sign up'}
                  </Button>
                  <FieldDescription className="text-center">
                    Already have an account?{' '}
                    <Link
                      to="/login"
                      search={{ redirect: redirectTarget }}
                      className="underline"
                    >
                      Log in
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
