-- ============================================
-- Test Users
-- ============================================

INSERT INTO users (
    name,
    address,
    shipping_address,
    email,
    phone
)
VALUES
    ('Test User1', 'Bangalore, Karnataka', 'Whitefield, Bangalore',
     'testuser1@example.com', '1234567890'),

    ('Test User2', 'Chennai, Tamil Nadu', 'OMR Road, Chennai',
     'testuser2@example.com', '0987654321'),

    ('Test User3', 'Hyderabad, Telangana', 'Gachibowli, Hyderabad',
     'testuser3@example.com', '9876543210'),

    ('Test User4', 'Pune, Maharashtra', 'Hinjewadi, Pune',
     'testuser4@example.com', '9123456780'),

    ('Test User5', 'Mumbai, Maharashtra', 'Andheri East, Mumbai',
     'testuser5@example.com', '9988776655')
    ON CONFLICT (email) DO NOTHING;


-- ============================================
-- Test Products
-- ============================================

INSERT INTO products (
    id,
    name,
    price,
    inventory_count,
    version
)
VALUES
    ('123', 'Laptop', 7000.00, 50, 0),
    ('124', 'Smartphone', 15000.00, 100, 0),
    ('125', 'Headphones', 2500.00, 200, 0),
    ('126', 'Keyboard', 1200.00, 150, 0),
    ('127', 'Mouse', 800.00, 300, 0)
    ON CONFLICT (id) DO NOTHING;