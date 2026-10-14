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
  slug?: string;
  is_visible: boolean | number;
  product_count?: number;
  version?: number;
}

export interface Product {
  product_id: number;
  category_id: number;
  category_name?: string;
  category_slug?: string;
  product_name: string;
  author?: string;
  product_description?: string;
  product_price: number | string;
  product_images: string[];
  product_attributes?: {
    format?: string[];
    [key: string]: any;
  };
  product_quantity?: number;
  in_stock?: boolean;
  max_quantity?: number;
  /** Admin-only fields */
  is_visible?: boolean;
  category_visible?: boolean;
  version?: number;
  created_at?: string;
  updated_at?: string;
}

/** One page of a paginated backend list (`{ items, page, pageSize, totalItems, totalPages }`). */
export interface Paged<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface CartItem {
  cart_item_id: number;
  user_id?: number;
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
  line_total?: number;
  available?: boolean;
  sufficient_stock?: boolean;
  valid_format?: boolean;
  max_quantity?: number;
  product_quantity?: number;
}

export interface CartResponse {
  cartItems: CartItem[];
  total: number;
  cartCount: number;
  valid?: boolean;
}

export interface WishlistItem {
  wishlist_id: number;
  user_id?: number;
  product_id: number;
  product_name: string;
  author?: string;
  product_price: number;
  product_images: string[];
  product_attributes?: any;
  category_name?: string;
  available?: boolean;
  in_stock?: boolean;
}

export interface Address {
  address_id: number;
  user_id?: number;
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

export interface PaymentMethod {
  payment_method_id: number;
  type: 'CREDIT' | 'DEBIT' | string;
  brand: string;
  cardholder_name: string;
  last4: string;
  expiry_month: number;
  expiry_year: number;
  currency: string;
  is_default: boolean;
  expired: boolean;
  created_at?: string;
}

export interface OrderItem {
  order_item_id: number;
  order_id?: number;
  product_id: number | null;
  quantity: number;
  unit_price: number | string;
  line_total?: number | string;
  product_name?: string;
  author?: string;
  category_name?: string;
  product_images?: string[];
  selected_attributes?: {
    format?: string;
    [key: string]: any;
  };
}

export type OrderStatus = 'pending' | 'processing' | 'completed' | 'cancelled';

export interface Order {
  order_id: number;
  user_id?: number;
  username?: string;
  email?: string;
  phone?: string;
  address_id?: number;
  total_price: number | string;
  total_items?: number;
  total_quantity?: number;
  payment_method: string;
  payment_status?: string;
  status: OrderStatus;
  created_at: string;
  items?: OrderItem[];
  address?: Address | null;
  saved_payment_method?: { brand?: string; last4?: string; type?: string } | null;
  /** Admin-only fields */
  version?: number;
  stock_released?: boolean;
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
  orderValue?: number | string;
  pendingOrders?: number;
  completedOrders?: number;
  cancelledOrders?: number;
  recentOrders: Order[];
}

export interface CustomerSummary {
  user_id: number;
  username: string;
  email: string;
  phone?: string;
  created_at?: string;
  order_count: number;
  total_spent: number;
  order_value: number;
  last_order_at?: string | null;
}
