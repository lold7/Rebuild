CREATE DATABASE IF NOT EXISTS bookstore;
USE bookstore;

-- Users (customers + admin)
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    role ENUM('customer', 'admin') DEFAULT 'customer',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Address Book
CREATE TABLE IF NOT EXISTS addresses (
    address_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    label VARCHAR(50) DEFAULT 'Home',
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(20),
    address_line VARCHAR(500) NOT NULL,
    city VARCHAR(100),
    province VARCHAR(100),
    postal_code VARCHAR(10),
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Categories
CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    category_image VARCHAR(500),
    is_visible BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Products (with author column)
CREATE TABLE IF NOT EXISTS products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    author VARCHAR(255),
    product_description TEXT,
    product_price DECIMAL(10, 2) NOT NULL,
    product_images JSON,
    product_attributes JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE CASCADE
);

-- Wishlist
CREATE TABLE IF NOT EXISTS wishlist (
    wishlist_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_wish (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

-- Shopping Cart
CREATE TABLE IF NOT EXISTS cart_items (
    cart_item_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT DEFAULT 1,
    selected_attributes JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

-- Orders
CREATE TABLE IF NOT EXISTS orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    address_id INT,
    total_price DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(50) DEFAULT 'credit_card',
    status ENUM('pending', 'completed', 'cancelled') DEFAULT 'completed',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (address_id) REFERENCES addresses(address_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    selected_attributes JSON,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- FAQ
CREATE TABLE IF NOT EXISTS faqs (
    faq_id INT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- SEED DATA
-- =============================================

-- Admin user (password: admin123)
INSERT INTO users (username, email, password_hash, phone, role) VALUES
('Admin', 'admin@bookstore.com', '$2b$10$8KzaNdKIMyOkASCKFb.yMeYSjPBiFBhp0glPgFdo1UGgx3cM.JTSe', '+66 800 000 000', 'admin');

-- Test customers (password: test123)
INSERT INTO users (username, email, password_hash, phone, role) VALUES
('John Smith', 'john@example.com', '$2b$10$8KzaNdKIMyOkASCKFb.yMeYSjPBiFBhp0glPgFdo1UGgx3cM.JTSe', '+66 812 345 678', 'customer'),
('Jane Doe', 'jane@example.com', '$2b$10$8KzaNdKIMyOkASCKFb.yMeYSjPBiFBhp0glPgFdo1UGgx3cM.JTSe', '+66 891 234 567', 'customer');

-- Addresses for test customers
INSERT INTO addresses (user_id, label, full_name, phone, address_line, city, province, postal_code, is_default) VALUES
(2, 'Home', 'John Smith', '+66 812 345 678', '123 Sukhumvit Road', 'Bangkok', 'Bangkok', '10110', TRUE),
(2, 'Work', 'John Smith', '+66 891 234 567', '456 Silom Tower, Floor 15', 'Bangkok', 'Bangkok', '10500', FALSE),
(3, 'Home', 'Jane Doe', '+66 891 234 567', '789 Ratchadaphisek Road', 'Bangkok', 'Bangkok', '10400', TRUE);

-- Categories
INSERT INTO categories (category_id, category_name, category_image, is_visible) VALUES
(1, 'Fiction', 'fiction.jpg', TRUE),
(2, 'Children', 'children.jpg', TRUE),
(3, 'Non-Fiction', 'nonfiction.jpg', TRUE),
(4, 'Mystery & Thriller', 'mystery.jpg', TRUE),
(5, 'Science & Technology', 'scitech.jpg', TRUE);

-- ===== FICTION (category_id: 1) =====
INSERT INTO products (product_id, category_id, product_name, author, product_price, product_images, product_attributes, product_description) VALUES
(1, 1, 'The Great Gatsby', 'F. Scott Fitzgerald', 18.00, '["fiction-01.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A story of the mysteriously wealthy Jay Gatsby and his love for Daisy Buchanan, set in the Jazz Age on Long Island.'),
(2, 1, 'To Kill a Mockingbird', 'Harper Lee', 21.00, '["fiction-02.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A gripping tale of racial injustice in the Deep South, seen through the eyes of young Scout Finch.'),
(3, 1, '1984', 'George Orwell', 15.00, '["fiction-03.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A dystopian novel set in a totalitarian society ruled by Big Brother, exploring themes of surveillance and control.'),
(4, 1, 'Pride and Prejudice', 'Jane Austen', 19.00, '["fiction-04.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The classic romance between Elizabeth Bennet and the proud Mr. Darcy in Regency-era England.'),
(5, 1, 'The Catcher in the Rye', 'J.D. Salinger', 22.00, '["fiction-05.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The story of Holden Caulfield, a teenage boy navigating the complexities of growing up in New York City.'),
(6, 1, 'One Hundred Years of Solitude', 'Gabriel Garcia Marquez', 17.00, '["fiction-06.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The multi-generational story of the Buendia family in the fictional town of Macondo.'),
(7, 1, 'Brave New World', 'Aldous Huxley', 20.00, '["fiction-07.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A futuristic society where humans are genetically modified and socially conditioned to serve a ruling order.'),
(8, 1, 'The Alchemist', 'Paulo Coelho', 24.00, '["fiction-08.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A shepherd boy travels from Spain to Egypt in search of treasure, discovering the meaning of life along the way.'),
(9, 1, 'Norwegian Wood', 'Haruki Murakami', 18.50, '["fiction-09.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A nostalgic story of loss and sexuality set in 1960s Tokyo, following Toru Watanabe''s college years.'),
(10, 1, 'The Book Thief', 'Markus Zusak', 19.99, '["fiction-10.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Narrated by Death, this is the story of Liesel Meminger, a girl living in Nazi Germany who steals books.'),
(11, 1, 'The Kite Runner', 'Khaled Hosseini', 23.00, '["fiction-11.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A haunting tale of friendship between two boys growing up in Kabul, torn apart by war and betrayal.'),
(12, 1, 'Kafka on the Shore', 'Haruki Murakami', 16.50, '["fiction-12.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A metaphysical journey following a teenage runaway and an old man who can talk to cats.');

-- ===== CHILDREN (category_id: 2) =====
INSERT INTO products (product_id, category_id, product_name, author, product_price, product_images, product_attributes, product_description) VALUES
(13, 2, 'Charlotte''s Web', 'E.B. White', 18.00, '["children-01.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The heartwarming story of a pig named Wilbur and his friendship with a spider named Charlotte.'),
(14, 2, 'Harry Potter and the Philosopher''s Stone', 'J.K. Rowling', 21.00, '["children-02.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A young orphan discovers he is a wizard and begins his magical education at Hogwarts School.'),
(15, 2, 'The Little Prince', 'Antoine de Saint-Exupery', 15.00, '["children-03.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A pilot stranded in the desert meets a young prince who tells of his travels across the universe.'),
(16, 2, 'Matilda', 'Roald Dahl', 19.00, '["children-04.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A brilliant young girl with telekinetic powers stands up to her neglectful parents and cruel headmistress.'),
(17, 2, 'The Chronicles of Narnia', 'C.S. Lewis', 22.00, '["children-05.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Four siblings discover a magical wardrobe that leads to the enchanted land of Narnia.'),
(18, 2, 'Charlie and the Chocolate Factory', 'Roald Dahl', 17.00, '["children-06.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Young Charlie Bucket wins a golden ticket to visit Willy Wonka''s extraordinary chocolate factory.'),
(19, 2, 'The Giving Tree', 'Shel Silverstein', 20.00, '["children-07.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A touching story about a tree that gives everything it has to a boy it loves throughout his life.'),
(20, 2, 'Where the Wild Things Are', 'Maurice Sendak', 24.00, '["children-08.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Max sails away to an island inhabited by wild creatures and becomes king of all wild things.'),
(21, 2, 'Goodnight Moon', 'Margaret Wise Brown', 18.50, '["children-09.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A beloved bedtime story where a young bunny says goodnight to everything in the great green room.'),
(22, 2, 'The Very Hungry Caterpillar', 'Eric Carle', 19.99, '["children-10.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Follow a caterpillar as it eats its way through a variety of foods before transforming into a butterfly.'),
(23, 2, 'Green Eggs and Ham', 'Dr. Seuss', 23.00, '["children-11.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Sam-I-Am tries to convince a grumpy character to try green eggs and ham in this classic rhyming tale.'),
(24, 2, 'The Cat in the Hat', 'Dr. Seuss', 16.50, '["children-12.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Two bored children are visited by a tall, mischievous cat who turns their house into a playground.');

-- ===== NON-FICTION (category_id: 3) =====
INSERT INTO products (product_id, category_id, product_name, author, product_price, product_images, product_attributes, product_description) VALUES
(25, 3, 'Sapiens: A Brief History', 'Yuval Noah Harari', 18.00, '["nonfiction-01.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'An exploration of how Homo sapiens came to dominate the earth through cognitive, agricultural, and scientific revolutions.'),
(26, 3, 'Educated', 'Tara Westover', 21.00, '["nonfiction-02.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A memoir about growing up in a survivalist family in Idaho and the transformative power of education.'),
(27, 3, 'Atomic Habits', 'James Clear', 15.00, '["nonfiction-03.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A proven framework for building good habits and breaking bad ones through tiny, incremental changes.'),
(28, 3, 'Thinking, Fast and Slow', 'Daniel Kahneman', 19.00, '["nonfiction-04.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Nobel laureate explores the two systems that drive the way we think: fast intuition and slow deliberation.'),
(29, 3, 'Becoming', 'Michelle Obama', 22.00, '["nonfiction-05.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The former First Lady shares her journey from the South Side of Chicago to the White House.'),
(30, 3, 'The Power of Now', 'Eckhart Tolle', 17.00, '["nonfiction-06.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A guide to spiritual enlightenment that emphasizes the importance of living in the present moment.'),
(31, 3, 'Quiet: The Power of Introverts', 'Susan Cain', 20.00, '["nonfiction-07.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'How introverts are dramatically undervalued in a world that prizes extroversion, and the power of silence.'),
(32, 3, 'The Subtle Art of Not Giving a F*ck', 'Mark Manson', 24.00, '["nonfiction-08.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A counterintuitive approach to living a good life by choosing what matters and letting go of the rest.'),
(33, 3, 'Outliers', 'Malcolm Gladwell', 18.50, '["nonfiction-09.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'An investigation into what makes high achievers different, revealing the role of culture, timing, and opportunity.'),
(34, 3, 'Freakonomics', 'Steven Levitt', 19.99, '["nonfiction-10.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A rogue economist explores the hidden side of everything using data-driven analysis of everyday life.'),
(35, 3, 'Man''s Search for Meaning', 'Viktor Frankl', 23.00, '["nonfiction-11.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A Holocaust survivor reflects on finding purpose and meaning even in the most horrific circumstances.'),
(36, 3, 'Guns, Germs, and Steel', 'Jared Diamond', 16.50, '["nonfiction-12.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Why did some civilizations conquer others? A groundbreaking look at geography, biology, and human history.');

-- ===== MYSTERY & THRILLER (category_id: 4) =====
INSERT INTO products (product_id, category_id, product_name, author, product_price, product_images, product_attributes, product_description) VALUES
(37, 4, 'Gone Girl', 'Gillian Flynn', 18.00, '["mt-01.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A husband becomes the prime suspect when his wife disappears on their fifth anniversary.'),
(38, 4, 'The Girl with the Dragon Tattoo', 'Stieg Larsson', 21.00, '["mt-02.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A journalist and a hacker investigate a wealthy family''s dark secrets spanning decades of corruption.'),
(39, 4, 'The Da Vinci Code', 'Dan Brown', 15.00, '["mt-03.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A symbologist uncovers a religious mystery hidden in the works of Leonardo da Vinci.'),
(40, 4, 'And Then There Were None', 'Agatha Christie', 19.00, '["mt-04.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Ten strangers are lured to an island where they are accused of murder and begin to die one by one.'),
(41, 4, 'The Silent Patient', 'Alex Michaelides', 22.00, '["mt-05.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A woman shoots her husband and then never speaks another word. A therapist is determined to uncover why.'),
(42, 4, 'In the Woods', 'Tana French', 17.00, '["mt-06.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A detective investigates a child''s murder in the same woods where his childhood friends vanished twenty years ago.'),
(43, 4, 'The Girl on the Train', 'Paula Hawkins', 20.00, '["mt-07.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A woman becomes entangled in a missing persons investigation that unravels her own troubled past.'),
(44, 4, 'Sharp Objects', 'Gillian Flynn', 24.00, '["mt-08.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A reporter returns to her hometown to cover the murders of two young girls and confronts her own demons.'),
(45, 4, 'Big Little Lies', 'Liane Moriarty', 18.50, '["mt-09.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Three women''s seemingly perfect lives unravel to a breaking point at a school trivia night.'),
(46, 4, 'The Woman in the Window', 'A.J. Finn', 19.99, '["mt-10.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'An agoraphobic woman believes she witnessed a crime in the apartment across the street.'),
(47, 4, 'Murder on the Orient Express', 'Agatha Christie', 23.00, '["mt-11.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Detective Hercule Poirot must solve a murder aboard a luxury train stranded in the snow.'),
(48, 4, 'The Hound of the Baskervilles', 'Arthur Conan Doyle', 16.50, '["mt-12.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'Sherlock Holmes investigates the legend of a supernatural hound haunting a family on the Devon moors.');

-- ===== SCIENCE & TECHNOLOGY (category_id: 5) =====
INSERT INTO products (product_id, category_id, product_name, author, product_price, product_images, product_attributes, product_description) VALUES
(49, 5, 'A Brief History of Time', 'Stephen Hawking', 18.00, '["sci-tech-01.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'From the Big Bang to black holes, Hawking explores the universe''s deepest mysteries for the general reader.'),
(50, 5, 'The Innovators', 'Walter Isaacson', 21.00, '["sci-tech-02.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The story of the pioneers who created the computer and the internet, from Ada Lovelace to Steve Jobs.'),
(51, 5, 'Cosmos', 'Carl Sagan', 15.00, '["sci-tech-03.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A journey through the universe exploring the evolution of galaxies, stars, life, and human civilization.'),
(52, 5, 'The Gene: An Intimate History', 'Siddhartha Mukherjee', 19.00, '["sci-tech-04.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A riveting history of the gene and a vision of both its promise and dangers for humanity''s future.'),
(53, 5, 'Clean Code', 'Robert C. Martin', 22.00, '["sci-tech-05.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A handbook of agile software craftsmanship with practical advice for writing clean, readable code.'),
(54, 5, 'The Structure of Scientific Revolutions', 'Thomas S. Kuhn', 17.00, '["sci-tech-06.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A groundbreaking analysis of the history of science that introduced the concept of paradigm shifts.'),
(55, 5, 'Astrophysics for People in a Hurry', 'Neil deGrasse Tyson', 20.00, '["sci-tech-07.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A concise guide to the universe''s greatest mysteries, from dark matter to the cosmic microwave background.'),
(56, 5, 'Steve Jobs', 'Walter Isaacson', 24.00, '["sci-tech-08.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The definitive biography of Apple''s co-founder, based on exclusive interviews with Jobs and those closest to him.'),
(57, 5, 'The Selfish Gene', 'Richard Dawkins', 18.50, '["sci-tech-09.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'A revolutionary look at evolution centered on the gene as the fundamental unit of natural selection.'),
(58, 5, 'Elon Musk', 'Ashlee Vance', 19.99, '["sci-tech-10.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The biography of the entrepreneur behind Tesla, SpaceX, and the quest to colonize Mars.'),
(59, 5, 'The Code Book', 'Simon Singh', 23.00, '["sci-tech-11.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'The history of cryptography from ancient Egypt to quantum computing and the science of secrecy.'),
(60, 5, 'Algorithms to Live By', 'Brian Christian', 16.50, '["sci-tech-12.png"]', '{"format": ["Paperback", "Hardcover", "E-Book"]}', 'How computer algorithms can help solve everyday human problems from organizing to decision-making.');

-- Sample orders for test customer (user_id: 2 = John Smith)
INSERT INTO orders (order_id, user_id, address_id, total_price, payment_method, status, created_at) VALUES
(1001, 2, 1, 55.00, 'Credit Card', 'completed', '2026-03-10 14:30:00'),
(1002, 2, 2, 39.00, 'Bank Transfer', 'completed', '2026-03-15 09:15:00'),
(1003, 2, 1, 42.00, 'Cash on Delivery', 'pending', '2026-03-18 16:45:00'),
(1004, 2, 1, 18.00, 'Credit Card', 'cancelled', '2026-03-20 11:00:00'),
(1005, 2, 1, 63.50, 'Credit Card', 'completed', '2026-03-22 13:20:00');

INSERT INTO order_items (order_id, product_id, quantity, unit_price, selected_attributes) VALUES
(1001, 1, 2, 18.00, '{"format": "Paperback"}'),
(1001, 5, 1, 22.00, '{"format": "Hardcover"}'),
(1002, 14, 1, 21.00, '{"format": "Hardcover"}'),
(1002, 3, 1, 15.00, '{"format": "Paperback"}'),
(1003, 37, 1, 18.00, '{"format": "Paperback"}'),
(1003, 8, 1, 24.00, '{"format": "E-Book"}'),
(1004, 49, 1, 18.00, '{"format": "Paperback"}'),
(1005, 25, 1, 18.00, '{"format": "Hardcover"}'),
(1005, 27, 1, 15.00, '{"format": "Paperback"}'),
(1005, 41, 1, 22.00, '{"format": "Paperback"}');

-- FAQs
INSERT INTO faqs (question, answer, sort_order) VALUES
('How do I place an order?', 'Simply browse our collection, add items to your cart, and proceed to checkout. You can select your shipping address and payment method before confirming your order.', 1),
('What payment methods do you accept?', 'We accept Credit Card, Bank Transfer, and Cash on Delivery. All transactions are secure and encrypted.', 2),
('How long does shipping take?', 'Standard shipping takes 3-5 business days within Bangkok and 5-7 business days for other provinces. Express shipping is available for an additional fee.', 3),
('Can I return or exchange a book?', 'Yes, you can return or exchange books within 30 days of purchase, provided they are in their original condition. Please contact our support team to initiate a return.', 4),
('Do you offer gift wrapping?', 'Yes, we offer gift wrapping for an additional 50 Baht per item. You can select this option during checkout.', 5),
('How can I track my order?', 'Once your order is shipped, you will receive a tracking number via email. You can also check your order status in the Order History section of your account.', 6),
('Are e-books available for all titles?', 'Most of our titles are available in Paperback, Hardcover, and E-Book formats. Check the product page for available formats.', 7),
('How do I create an account?', 'Click the "Register" button on the login page and fill in your details including username, email, and password. Once registered, you can start shopping immediately.', 8),
('Can I save books for later?', 'Yes! Use the wishlist feature to save books you are interested in. Click the heart icon on any product to add it to your wishlist.', 9),
('How do I contact customer support?', 'You can reach us through our Contact page, by email at support@inwza-bookstore.com, or by phone at +66 2 123 4567 during business hours (Mon-Fri 9AM-6PM).', 10),
('Do you ship internationally?', 'Currently, we only ship within Thailand. We are working on expanding our shipping to other countries in Southeast Asia.', 11),
('Is there a minimum order amount?', 'No, there is no minimum order amount. You can order as few or as many books as you like.', 12);
