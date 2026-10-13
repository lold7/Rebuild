import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/models';

@Component({
  selector: 'app-all-products',
  standalone: true,
  imports: [CommonModule, RouterModule, MatPaginatorModule],
  templateUrl: './all-products.component.html',
  styleUrls: ['./all-products.component.scss']
})
export class AllProductsComponent implements OnInit {
  private productService = inject(ProductService);
  private router = inject(Router);

  products: Product[] = [];
  displayedProducts: Product[] = [];
  pageSize = 12;
  currentPage = 0;

  ngOnInit(): void {
    this.productService.getProducts().subscribe({
      next: (data) => {
        this.products = data;
        this.updatePage();
      },
      error: () => {}
    });
  }

  updatePage(): void {
    const start = this.currentPage * this.pageSize;
    this.displayedProducts = this.products.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePage();
    window.scrollTo({ top: 200, behavior: 'smooth' });
  }

  goToProduct(id: number): void {
    this.router.navigate(['/product', id]);
  }

  getImageUrl(images: string[] | string): string {
    if (Array.isArray(images) && images.length > 0) {
      return `/images/products/${images[0]}`;
    }
    if (typeof images === 'string') {
      try {
        const arr = JSON.parse(images);
        return `/images/products/${arr[0]}`;
      } catch (_) {
        return `/images/products/${images}`;
      }
    }
    return '/images/Logo.png';
  }
}
