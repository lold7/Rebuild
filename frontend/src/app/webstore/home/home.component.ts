import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, MatExpansionModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  private productService = inject(ProductService);
  private router = inject(Router);

  products: Product[] = [];
  currentSlide = 0;
  slideInterval: any;

  ngOnInit(): void {
    this.productService.getProducts({ featured: true }).subscribe({
      next: (data) => {
        this.products = data;
        this.startCarousel();
      },
      error: () => {}
    });
  }

  get slideCount(): number {
    return Math.ceil(Math.min(this.products.length, 9) / 3);
  }

  getSlideProducts(slideIndex: number): Product[] {
    const start = slideIndex * 3;
    return this.products.slice(start, start + 3);
  }

  startCarousel(): void {
    if (this.slideInterval) clearInterval(this.slideInterval);
    this.slideInterval = setInterval(() => {
      this.nextSlide();
    }, 3500);
  }

  nextSlide(): void {
    if (this.slideCount > 0) {
      this.currentSlide = (this.currentSlide + 1) % this.slideCount;
    }
  }

  prevSlide(): void {
    if (this.slideCount > 0) {
      this.currentSlide = (this.currentSlide - 1 + this.slideCount) % this.slideCount;
    }
  }

  goToSlide(index: number): void {
    this.currentSlide = index;
    this.startCarousel();
  }

  goToProduct(id: number): void {
    this.router.navigate(['/product', id]);
  }

  getImageUrl(images: string[] | string): string {
    return productImageUrl(images);
  }
}
