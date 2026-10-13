export interface User {
  user_id: number;
  username: string;
  email: string;
  phone?: string;
  role: 'customer' | 'admin';
  avatar_url?: string;
  cartCount?: number;
  wishlistCount?: number;
}

export interface Category {
  category_id: number;
  category_name: string;
  category_image?: string;
  is_visible: boolean | number;
  product_count?: number;
}

export interface Product {
  product_id: number;
  category_id: number;
  category_name?: string;
  product_name: string;
  author?: string;
  product_description?: string;
  product_price: number | string;
  product_images: string[];
  product_attributes?: {
    format?: string[];
    [key: string]: any;
  };
  created_at?: string;
  updated_at?: string;
}

export interface CartItem {
  cart_item_id: number;
  user_id: number;
  product_id: number;
  quantity: number;
  product_name: string;
  author?: string;
  product_price: number;
  product_images: string[];
  selected_attributes?: {
    format?: string;
    [key: string]: any;
  };
}

export interface CartResponse {
  cartItems: CartItem[];
  total: number;
  cartCount: number;
}

export interface WishlistItem {
  wishlist_id: number;
  user_id: number;
  product_id: number;
  product_name: string;
  author?: string;
  product_price: number;
  product_images: string[];
  product_attributes?: any;
  category_name?: string;
}

export interface Address {
  address_id: number;
  user_id: number;
  label: string;
  full_name: string;
  phone?: string;
  address_line: string;
  city?: string;
  province?: string;
  postal_code?: string;
  is_default: boolean | number;
  created_at?: string;
}

export interface OrderItem {
  order_item_id: number;
  order_id: number;
  product_id: number;
  quantity: number;
  unit_price: number | string;
  product_name?: string;
  author?: string;
  product_images?: string[];
  selected_attributes?: {
    format?: string;
    [key: string]: any;
  };
}

export interface Order {
  order_id: number;
  user_id: number;
  username?: string;
  email?: string;
  address_id?: number;
  total_price: number | string;
  payment_method: string;
  status: 'pending' | 'completed' | 'cancelled';
  created_at: string;
  items?: OrderItem[];
  address?: Address | null;
}

export interface FAQ {
  faq_id: number;
  question: string;
  answer: string;
  sort_order: number;
}

export interface DashboardStats {
  totalOrders: number;
  totalRevenue: number | string;
  totalCustomers: number;
  totalProducts: number;
  recentOrders: Order[];
}
