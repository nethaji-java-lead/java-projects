INSERT INTO users (
    name,
    address,
    shipping_address,
    email,
    phone
)
VALUES
    (
        'Test User1',
        'Bangalore, Karnataka',
        'Whitefield, Bangalore',
        'testuser1@example.com',
        '1234567890'
    ),
    (
        'Test User2',
        'Chennai, Tamil Nadu',
        'OMR Road, Chennai',
        'testuser2@example.com',
        '0987654321'
    )
ON CONFLICT (email) DO NOTHING;