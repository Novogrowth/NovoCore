import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'

import App from './App.tsx'
import { Placeholder } from './pages/placeholder.tsx'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<App />}>
          <Route index element={<Placeholder title="Overview" />} />
          <Route
            path="section-two"
            element={<Placeholder title="Section Two" />}
          />
          <Route
            path="section-three"
            element={<Placeholder title="Section Three" />}
          />
        </Route>
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
