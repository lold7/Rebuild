import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';

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
    return productImageUrl(images);
  }
}
