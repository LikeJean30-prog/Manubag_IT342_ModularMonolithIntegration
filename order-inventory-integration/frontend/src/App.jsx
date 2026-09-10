import { useState } from 'react'
import './index.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const PRODUCTS = [
  { productId: 'P100', name: 'Wireless Mouse' },
  { productId: 'P200', name: 'Mechanical Keyboard' },
  { productId: 'P300', name: 'USB-C Hub' },
]

export default function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].productId)
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setResult(null)
    setLoading(true)

    try {
      const response = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      })

      if (!response.ok) {
        const body = await response.text()
        throw new Error(`Server responded ${response.status}: ${body}`)
      }

      const data = await response.json()
      setResult(data)
    } catch (err) {
      setError(err.message || 'Could not reach the order service.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <div className="slip">
        <div className="slip-heading">
          <h1 className="slip-title">Order requisition</h1>
          <p className="slip-subtitle">POST /api/orders — Order module → Inventory module (in-process)</p>
        </div>

        <form className="slip-body" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="productId">Product</label>
            <div className="field-control">
              <select
                id="productId"
                value={productId}
                onChange={(e) => setProductId(e.target.value)}
              >
                {PRODUCTS.map((p) => (
                  <option key={p.productId} value={p.productId}>
                    {p.productId} — {p.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="field">
            <label htmlFor="quantity">Quantity</label>
            <div className="field-control">
              <input
                id="quantity"
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                required
              />
              <span className="field-hint">units requested</span>
            </div>
          </div>

          <div className="submit-row">
            <button className="submit-button" type="submit" disabled={loading}>
              {loading ? 'Submitting…' : 'Submit order'}
            </button>
          </div>

          {error && <p className="error-note">{error}</p>}
        </form>

        {result && (
          <div className="result">
            <span className={`result-status ${result.status}`}>{result.status}</span>

            {result.reason && <p className="result-reason">{result.reason}</p>}

            {result.inventory && (
              <div className="result-inventory">
                <span>{result.inventory.productId} — {result.inventory.name}</span>
                <span>stock: {result.inventory.stock}</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
