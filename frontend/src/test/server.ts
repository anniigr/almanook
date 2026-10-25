import { setupServer } from 'msw/node'

/** MSW server; each test registers the handlers it needs (unhandled requests fail the test). */
export const server = setupServer()
