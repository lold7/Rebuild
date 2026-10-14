import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';

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
  private route = inject(ActivatedRoute);

  /** Products on the current page (the server returns 15 per page). */
  displayedProducts: Product[] = [];
  totalItems = 0;
  pageSize = 15;
  currentPage = 0;

  ngOnInit(): void {
    // ?page=N (1-based) keeps the page in the URL; pages past the end are simply empty.
    this.route.queryParamMap.subscribe(params => {
      const page = Number(params.get('page')) || 1;
      this.currentPage = Math.max(0, page - 1);
      this.loadPage();
    });
  }

  loadPage(): void {
    this.productService.getProductPage(this.currentPage + 1).subscribe({
      next: (res) => {
        this.displayedProducts = res.items;
        this.totalItems = res.totalItems;
        this.pageSize = res.pageSize;
      },
      error: () => {
        this.displayedProducts = [];
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { page: event.pageIndex + 1 } });
    window.scrollTo({ top: 200, behavior: 'smooth' });
  }

  goToProduct(id: number): void {
    this.router.navigate(['/product', id]);
  }

  getImageUrl(images: string[] | string): string {
    return productImageUrl(images);
  }
}
