import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/models';

@Component({
  selector: 'app-search-results',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './search-results.component.html',
  styleUrls: ['./search-results.component.scss']
})
export class SearchResultsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private productService = inject(ProductService);

  query = '';
  products: Product[] = [];
  isLoading = true;

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.query = params['q'] || '';
      if (this.query) {
        this.performSearch(this.query);
      } else {
        this.products = [];
        this.isLoading = false;
      }
    });
  }

  performSearch(query: string): void {
    this.isLoading = true;
    this.productService.searchProducts(query).subscribe({
      next: (res) => {
        this.products = res.products || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
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
