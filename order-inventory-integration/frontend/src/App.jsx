import { useCallback, useEffect, useState } from 'react'
import './index.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const LOW_STOCK_THRESHOLD = 5

async function readJsonOrThrow(response) {
  if (!response.ok) {
    let message = `Server responded ${response.status}`
    try {
      const body = await response.json()
      if (body?.error) message = body.error
    } catch {
      // Ignore invalid JSON responses
    }
    throw new Error(message)
  }
  return response.json()
}

function formatTime(isoString) {
  return new Date(isoString).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function App() {
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])
  const [cart, setCart] = useState([])
  const [selectedProductId, setSelectedProductId] = useState('')
  const [selectedQuantity, setSelectedQuantity] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)
  const [orderResult, setOrderResult] = useState(null)
  const [error, setError] = useState(null)

  const refreshAll = useCallback(async () => {
    try {
      const [inventoryData, ordersData, notificationsData] = await Promise.all([
        fetch(`${API_BASE_URL}/api/inventory`).then(readJsonOrThrow),
        fetch(`${API_BASE_URL}/api/orders`).then(readJsonOrThrow),
        fetch(`${API_BASE_URL}/api/notifications`).then(readJsonOrThrow),
      ])

      setInventory(inventoryData)
      setOrders(ordersData)
      setNotifications(notificationsData)
      setError(null)

      if (!selectedProductId && inventoryData.length > 0) {
        setSelectedProductId(inventoryData[0].productId)
      }
    } catch (err) {
      setError(err.message || 'Could not reach the order service.')
    }
  }, [selectedProductId])

  useEffect(() => {
    refreshAll()
  }, [refreshAll])

  function addToCart() {
    if (!selectedProductId || selectedQuantity < 1) return

    setCart((current) => {
      const existing = current.find(
        (line) => line.productId === selectedProductId
      )

      if (existing) {
        return current.map((line) =>
          line.productId === selectedProductId
            ? {
                ...line,
                quantity: line.quantity + Number(selectedQuantity),
              }
            : line
        )
      }

      return [
        ...current,
        {
          productId: selectedProductId,
          quantity: Number(selectedQuantity),
        },
      ]
    })

    setSelectedQuantity(1)
  }

  function removeFromCart(productId) {
    setCart((current) =>
      current.filter((line) => line.productId !== productId)
    )
  }

  function productName(productId) {
    return (
      inventory.find((item) => item.productId === productId)?.name ??
      productId
    )
  }

  async function submitOrder() {
    if (cart.length === 0) return

    setSubmitting(true)
    setOrderResult(null)
    setError(null)

    try {
      const response = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ items: cart }),
      })

      const data = await readJsonOrThrow(response)

      setOrderResult(data)
      setCart([])

      await refreshAll()
    } catch (err) {
      setError(err.message || 'Could not submit the order.')
    } finally {
      setSubmitting(false)
    }
  }

  async function cancelOrder(orderId) {
    setCancellingId(orderId)
    setError(null)

    try {
      const response = await fetch(
        `${API_BASE_URL}/api/orders/${orderId}/cancel`,
        { method: 'POST' }
      )

      await readJsonOrThrow(response)
      await refreshAll()
    } catch (err) {
      setError(err.message || 'Could not cancel the order.')
    } finally {
      setCancellingId(null)
    }
  }

  // Products with stock at or below the low-stock threshold
  const lowStockItems = inventory.filter(
    (item) => item.stock <= LOW_STOCK_THRESHOLD
  )

  return (
    <div className="page">
      <div className="board">
        <header className="board-heading">
          <h1 className="board-title">Order &amp; Inventory Desk</h1>
        </header>

        {error && <p className="banner-error">{error}</p>}

        {/* Low Stock Alert */}
        {lowStockItems.length > 0 && (
          <div className="low-stock-alert" role="alert">
            <div className="low-stock-alert-icon">⚠</div>

            <div className="low-stock-alert-content">
              <strong>Low Stock Alert</strong>

              <p>
                {lowStockItems.length === 1
                  ? '1 product is running low on stock.'
                  : `${lowStockItems.length} products are running low on stock.`}
              </p>

              <ul className="low-stock-alert-list">
                {lowStockItems.map((item) => (
                  <li key={item.productId}>
                    <strong>{item.name}</strong>
                    <span>
                      {item.stock === 0
                        ? 'Out of stock'
                        : `${item.stock} unit${item.stock === 1 ? '' : 's'} remaining`}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}

        <div className="board-grid">
          <section className="panel">
            <h2 className="panel-title">New order</h2>

            <div className="cart-builder">
              <select
                value={selectedProductId}
                onChange={(e) => setSelectedProductId(e.target.value)}
              >
                {inventory.map((item) => (
                  <option key={item.productId} value={item.productId}>
                    {item.productId} — {item.name} ({item.stock} in stock)
                  </option>
                ))}
              </select>

              <input
                type="number"
                min="1"
                value={selectedQuantity}
                onChange={(e) => setSelectedQuantity(e.target.value)}
              />

              <button
                type="button"
                className="secondary-button"
                onClick={addToCart}
              >
                Add to cart
              </button>
            </div>

            {cart.length === 0 ? (
              <p className="empty-note">
                Cart is empty — add a product above.
              </p>
            ) : (
              <ul className="cart-list">
                {cart.map((line) => (
                  <li key={line.productId}>
                    <span>
                      {line.productId} — {productName(line.productId)}
                    </span>

                    <span>× {line.quantity}</span>

                    <button
                      type="button"
                      className="link-button"
                      onClick={() => removeFromCart(line.productId)}
                    >
                      remove
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <button
              type="button"
              className="submit-button"
              disabled={cart.length === 0 || submitting}
              onClick={submitOrder}
            >
              {submitting ? 'Submitting…' : 'Submit order'}
            </button>

            {orderResult && (
              <div className="result">
                <span
                  className={`result-status ${orderResult.status}`}
                >
                  {orderResult.status}
                </span>

                {orderResult.reason && (
                  <p className="result-reason">{orderResult.reason}</p>
                )}

                <ul className="result-items">
                  {orderResult.items.map((line) => (
                    <li key={line.productId}>
                      {line.productId}: {line.outcome}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>

          <section className="panel">
            <h2 className="panel-title">Live inventory</h2>

            <table className="inventory-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Name</th>
                  <th>Stock</th>
                </tr>
              </thead>

              <tbody>
                {inventory.map((item) => {
                  const isLowStock = item.stock <= LOW_STOCK_THRESHOLD
                  const isOutOfStock = item.stock === 0

                  return (
                    <tr
                      key={item.productId}
                      className={
                        isOutOfStock
                          ? 'row-out-of-stock'
                          : isLowStock
                            ? 'row-low-stock'
                            : ''
                      }
                    >
                      <td>{item.productId}</td>
                      <td>{item.name}</td>
                      <td>
                        <span className="stock-value">
                          {item.stock}
                        </span>

                        {isOutOfStock && (
                          <span className="stock-warning">
                            Out of stock
                          </span>
                        )}

                        {!isOutOfStock && isLowStock && (
                          <span className="stock-warning">
                            Low stock
                          </span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </section>

          <section className="panel">
            <h2 className="panel-title">Order history</h2>

            {orders.length === 0 ? (
              <p className="empty-note">No orders yet.</p>
            ) : (
              <ul className="order-history">
                {orders.map((order) => (
                  <li key={order.orderId}>
                    <div className="order-history-row">
                      <span
                        className={`result-status ${order.status}`}
                      >
                        {order.status}
                      </span>

                      <span className="order-id">
                        O{order.orderId}
                      </span>

                      <span className="order-time">
                        {formatTime(order.createdAt)}
                      </span>

                      {order.status === 'CONFIRMED' && (
                        <button
                          type="button"
                          className="link-button"
                          disabled={cancellingId === order.orderId}
                          onClick={() => cancelOrder(order.orderId)}
                        >
                          {cancellingId === order.orderId
                            ? 'cancelling…'
                            : 'cancel'}
                        </button>
                      )}
                    </div>

                    <div className="order-history-items">
                      {order.items
                        .map(
                          (line) =>
                            `${line.productId} ×${line.quantity}`
                        )
                        .join(', ')}
                    </div>

                    {order.reason && (
                      <div className="order-history-reason">
                        {order.reason}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="panel">
            <h2 className="panel-title">Activity feed</h2>

            {notifications.length === 0 ? (
              <p className="empty-note">No activity yet.</p>
            ) : (
              <ul className="activity-feed">
                {notifications.map((note) => (
                  <li key={note.notificationId}>
                    <span className="activity-time">
                      {formatTime(note.createdAt)}
                    </span>

                    <span>{note.message}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}
