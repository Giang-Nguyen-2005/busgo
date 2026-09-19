import { createBrowserRouter } from 'react-router-dom'
import { FoundationPage } from '../pages/FoundationPage'

export const router = createBrowserRouter([
  { path: '/', element: <FoundationPage /> },
])
