/* ===========================================
   Mock Data: Sample Orders (for order history)
   =========================================== */
window.MOCK_ORDERS = [
    {
        order_id: 1001,
        created_at: "2026-03-10T14:30:00",
        total_price: 55.00,
        status: "completed",
        payment_method: "Credit Card",
        address: { label: "Home", full_name: "John Smith", address_line: "123 Main Street", city: "Bangkok", province: "Bangkok", postal_code: "10110" },
        items: [
            { product_id: 1, product_name: "The Great Gatsby", quantity: 2, unit_price: 18.00, format: "Paperback", product_image: "fiction-01.png" },
            { product_id: 5, product_name: "The Catcher in the Rye", quantity: 1, unit_price: 22.00, format: "Hardcover", product_image: "fiction-05.png" }
        ]
    },
    {
        order_id: 1002,
        created_at: "2026-03-15T09:15:00",
        total_price: 39.00,
        status: "completed",
        payment_method: "Bank Transfer",
        address: { label: "Work", full_name: "John Smith", address_line: "456 Office Tower", city: "Bangkok", province: "Bangkok", postal_code: "10330" },
        items: [
            { product_id: 14, product_name: "Harry Potter and the Philosopher's Stone", quantity: 1, unit_price: 21.00, format: "Hardcover", product_image: "children-02.png" },
            { product_id: 3, product_name: "1984", quantity: 1, unit_price: 15.00, format: "Paperback", product_image: "fiction-03.png" }
        ]
    },
    {
        order_id: 1003,
        created_at: "2026-03-18T16:45:00",
        total_price: 42.00,
        status: "pending",
        payment_method: "Cash on Delivery",
        address: { label: "Home", full_name: "John Smith", address_line: "123 Main Street", city: "Bangkok", province: "Bangkok", postal_code: "10110" },
        items: [
            { product_id: 37, product_name: "Gone Girl", quantity: 1, unit_price: 18.00, format: "Paperback", product_image: "mt-01.png" },
            { product_id: 8, product_name: "The Alchemist", quantity: 1, unit_price: 24.00, format: "E-Book", product_image: "fiction-08.png" }
        ]
    },
    {
        order_id: 1004,
        created_at: "2026-03-20T11:00:00",
        total_price: 18.00,
        status: "cancelled",
        payment_method: "Credit Card",
        address: { label: "Home", full_name: "John Smith", address_line: "123 Main Street", city: "Bangkok", province: "Bangkok", postal_code: "10110" },
        items: [
            { product_id: 49, product_name: "A Brief History of Time", quantity: 1, unit_price: 18.00, format: "Paperback", product_image: "sci-tech-01.png" }
        ]
    },
    {
        order_id: 1005,
        created_at: "2026-03-22T13:20:00",
        total_price: 63.50,
        status: "completed",
        payment_method: "Credit Card",
        address: { label: "Home", full_name: "John Smith", address_line: "123 Main Street", city: "Bangkok", province: "Bangkok", postal_code: "10110" },
        items: [
            { product_id: 25, product_name: "Sapiens: A Brief History", quantity: 1, unit_price: 18.00, format: "Hardcover", product_image: "nonfiction-01.png" },
            { product_id: 27, product_name: "Atomic Habits", quantity: 1, unit_price: 15.00, format: "Paperback", product_image: "nonfiction-03.png" },
            { product_id: 41, product_name: "The Silent Patient", quantity: 1, unit_price: 22.00, format: "Paperback", product_image: "mt-05.png" }
        ]
    }
];
