import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../../core/services/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { ProductService } from '../../../core/services/product.service';
import { Category } from '../../../core/models/models';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    MatMenuModule,
    MatButtonModule,
    MatIconModule,
    MatBadgeModule,
    MatDividerModule
  ],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent implements OnInit {
  authService = inject(AuthService);
  cartService = inject(CartService);
  router = inject(Router);
  private productService = inject(ProductService);

  categories: Category[] = [];
  searchQuery = '';

  /** The Categories menu lists the visible categories stored in the database. */
  ngOnInit(): void {
    this.loadCategories();
    // Refresh after navigation so newly added or hidden categories show up.
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.loadCategories());
  }

  private loadCategories(): void {
    this.productService.getCategories().subscribe({
      next: (list) => this.categories = list,
      error: () => {}
    });
  }
  isMobileMenuOpen = false;

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  onSearch(): void {
    if (this.searchQuery.trim()) {
      this.router.navigate(['/search'], { queryParams: { q: this.searchQuery.trim() } });
      this.searchQuery = '';
      this.isMobileMenuOpen = false;
    }
  }

  logout(): void {
    this.authService.logout();
    this.isMobileMenuOpen = false;
  }
}
