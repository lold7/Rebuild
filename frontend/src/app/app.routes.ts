import { Routes } from '@angular/router';
import { authGuard, customerGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

// Webstore components
import { HomeComponent } from './webstore/home/home.component';
import { AllProductsComponent } from './webstore/all-products/all-products.component';
import { AllCategoriesComponent } from './webstore/all-categories/all-categories.component';
import { CategoryComponent } from './webstore/category/category.component';
import { ProductDetailComponent } from './webstore/product-detail/product-detail.component';
import { CartComponent } from './webstore/cart/cart.component';
import { CheckoutComponent } from './webstore/checkout/checkout.component';
import { ChoosePaymentComponent } from './webstore/choose-payment/choose-payment.component';
import { WishlistComponent } from './webstore/wishlist/wishlist.component';
import { ProfileComponent } from './webstore/profile/profile.component';
import { AddressBookComponent } from './webstore/address-book/address-book.component';
import { OrderHistoryComponent } from './webstore/order-history/order-history.component';
import { SearchResultsComponent } from './webstore/search-results/search-results.component';
import { ContactComponent } from './webstore/contact/contact.component';
import { FaqComponent } from './webstore/faq/faq.component';
import { ConditionComponent } from './webstore/condition/condition.component';
import { OrderDetailComponent } from './webstore/order-detail/order-detail.component';
import { NotFoundComponent } from './webstore/not-found/not-found.component';
import { ForbiddenComponent } from './webstore/forbidden/forbidden.component';

// Auth components
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { AdminLoginComponent } from './auth/admin-login/admin-login.component';
import { ForgotPasswordComponent } from './auth/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './auth/reset-password/reset-password.component';
import { AdminRegisterComponent } from './auth/admin-register/admin-register.component';

// Backoffice components
import { DashboardComponent } from './backoffice/dashboard/dashboard.component';
import { AdminCategoriesComponent } from './backoffice/categories/admin-categories.component';
import { AdminProductsComponent } from './backoffice/products/admin-products.component';
import { InventoryComponent } from './backoffice/inventory/inventory.component';
import { AdminOrdersComponent } from './backoffice/orders/admin-orders.component';
import { CustomersComponent } from './backoffice/customers/customers.component';
import { AdminProfileComponent } from './backoffice/profile/admin-profile.component';

export const routes: Routes = [
  // Webstore Routes
  { path: '', component: HomeComponent },
  { path: 'products', component: AllProductsComponent },
  { path: 'categories', component: AllCategoriesComponent },
  { path: 'category/:slug', component: CategoryComponent },
  { path: 'product/:id', component: ProductDetailComponent },
  { path: 'cart', component: CartComponent },
  { path: 'checkout', component: CheckoutComponent, canActivate: [authGuard] },
  { path: 'checkout/payment', component: ChoosePaymentComponent, canActivate: [authGuard] },
  { path: 'account/wishlist', component: WishlistComponent, canActivate: [authGuard] },
  { path: 'account/profile', component: ProfileComponent, canActivate: [customerGuard] },
  { path: 'account/addresses', component: AddressBookComponent, canActivate: [authGuard] },
  { path: 'account/payment-methods', component: ChoosePaymentComponent, canActivate: [authGuard], data: { mode: 'manage' } },
  { path: 'account/orders', component: OrderHistoryComponent, canActivate: [authGuard] },
  { path: 'account/orders/:id', component: OrderDetailComponent, canActivate: [authGuard] },
  { path: 'search', component: SearchResultsComponent },
  { path: 'contact', component: ContactComponent },
  { path: 'faq', component: FaqComponent },
  { path: 'condition', component: ConditionComponent },

  // Auth Routes
  { path: 'auth/login', component: LoginComponent },
  { path: 'auth/register', component: RegisterComponent },
  { path: 'auth/forgot-password', component: ForgotPasswordComponent },
  { path: 'auth/reset-password/:token', component: ResetPasswordComponent },
  { path: 'backoffice/login', component: AdminLoginComponent },
  { path: 'auth/admin-login', redirectTo: 'backoffice/login', pathMatch: 'full' },

  // Backoffice Routes
  { path: 'backoffice', component: DashboardComponent, canActivate: [adminGuard] },
  { path: 'backoffice/categories', component: AdminCategoriesComponent, canActivate: [adminGuard] },
  { path: 'backoffice/products', component: AdminProductsComponent, canActivate: [adminGuard] },
  { path: 'backoffice/inventory', component: InventoryComponent, canActivate: [adminGuard] },
  { path: 'backoffice/inventory/:id', component: InventoryComponent, canActivate: [adminGuard] },
  { path: 'backoffice/orders', component: AdminOrdersComponent, canActivate: [adminGuard] },
  { path: 'backoffice/customers', component: CustomersComponent, canActivate: [adminGuard] },
  { path: 'backoffice/profile', component: AdminProfileComponent, canActivate: [adminGuard] },
  { path: 'backoffice/register', component: AdminRegisterComponent, canActivate: [adminGuard] },

  // Error pages
  { path: 'forbidden', component: ForbiddenComponent },
  { path: 'not-found', component: NotFoundComponent },
  { path: '**', component: NotFoundComponent }
];
