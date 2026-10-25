import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ApiError } from '../../api/client'
import { useLogin, useMe, useRegister } from '../../api/hooks'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'

export function LoginPage() {
  const { data: me } = useMe()
  const location = useLocation()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const login = useLogin()
  const register = useRegister()
  const mutation = mode === 'login' ? login : register
  const error = mutation.error instanceof ApiError ? mutation.error : null
  const fieldErrors = error?.fieldErrors() ?? {}

  if (me) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from && from !== '/login' ? from : '/collection'} replace />
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (mode === 'login') login.mutate({ email, password })
    else register.mutate({ email, password, displayName: name })
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4">
      <div className="w-full max-w-sm space-y-8">
        <div className="space-y-2 text-center">
          <img src="/favicon.svg" alt="" className="mx-auto size-12" />
          <h1 className="font-serif text-3xl text-text">Almanook</h1>
          <p className="text-sm text-muted">Your almanac of places and plans.</p>
        </div>
        <form
          onSubmit={submit}
          className="space-y-4 rounded-2xl border border-line bg-surface p-6 shadow-card"
          noValidate
        >
          <h2 className="text-base font-medium text-text">
            {mode === 'login' ? 'Sign in' : 'Create an account'}
          </h2>
          {mode === 'register' && (
            <TextField
              label="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
              error={fieldErrors.displayName}
              required
            />
          )}
          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            error={fieldErrors.email}
            required
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            hint={mode === 'register' ? 'At least 10 characters' : undefined}
            error={fieldErrors.password}
            required
          />
          {error && Object.keys(fieldErrors).length === 0 && (
            <p role="alert" className="text-sm text-danger">
              {error.message}
            </p>
          )}
          <Button type="submit" variant="primary" className="w-full" loading={mutation.isPending}>
            {mode === 'login' ? 'Sign in' : 'Create account'}
          </Button>
        </form>
        <p className="text-center text-sm text-muted">
          {mode === 'login' ? 'New here?' : 'Already have an account?'}{' '}
          <button
            type="button"
            className="text-accent hover:underline"
            onClick={() => {
              mutation.reset()
              setMode(mode === 'login' ? 'register' : 'login')
            }}
          >
            {mode === 'login' ? 'Create an account' : 'Sign in'}
          </button>
        </p>
      </div>
    </main>
  )
}
