import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ProductService } from '../../core/services/product.service';
import { Category } from '../../core/models/models';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-all-categories',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule],
  templateUrl: './all-categories.component.html',
  styleUrls: ['./all-categories.component.scss']
})
export class AllCategoriesComponent implements OnInit {
  private productService = inject(ProductService);

  categories: Category[] = [];

  defaultImages: { [key: string]: string } = {
    'Fiction': 'https://9kafe.com/wp-content/uploads/2024/08/lord-of-the-mysteries-wn.jpg',
    'Non-Fiction': 'https://images.unsplash.com/photo-1589998059171-988d887df646?q=80&w=800&auto=format&fit=crop',
    'Children': 'https://m.media-amazon.com/images/I/91Rd6IjcSWL._AC_UF1000,1000_QL80_.jpg',
    'Mystery & Thriller': 'https://images.unsplash.com/photo-1618666012174-83b441c0bc76?q=80&w=800&auto=format&fit=crop',
    'Science & Technology': 'https://images.unsplash.com/photo-1532094349884-543bc11b234d?q=80&w=800&auto=format&fit=crop'
  };

  defaultDescs: { [key: string]: string } = {
    'Fiction': 'Stories that transport you to other worlds.',
    'Non-Fiction': 'Real stories and ideas that shape how we think.',
    'Children': 'Young adult and coming-of-age tales for every reader',
    'Mystery & Thriller': 'Dark vibes and unexpected twists keep you guessing until the end.',
    'Science & Technology': 'Explore how ideas shape the world in our grid system and discovery.'
  };

  ngOnInit(): void {
    this.productService.getCategories().subscribe({
      next: (data) => {
        this.categories = data;
      },
      error: () => {}
    });
  }

  private readonly knownNames = ['fiction', 'non-fiction', 'children', 'mystery & thriller', 'science & technology'];

  getCategory(name: string): Category | undefined {
    return this.categories.find(c => c.category_name.toLowerCase() === name.toLowerCase());
  }

  /** Categories added after the original five are listed under "More categories". */
  get moreCategories(): Category[] {
    return this.categories.filter(c => !this.knownNames.includes(c.category_name.toLowerCase()));
  }

  getSlug(name: string): string {
    const category = this.getCategory(name);
    if (category?.slug) return category.slug;
    const map: { [key: string]: string } = {
      'Fiction': 'fiction',
      'Children': 'children',
      'Non-Fiction': 'nonfiction',
      'Mystery & Thriller': 'mystery',
      'Science & Technology': 'science'
    };
    return map[name] || name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
  }

  /** Uses a category's own image when it is a web URL or an uploaded cover, otherwise the page's default artwork. */
  imageFor(category: Category, fallbackKey: string): string {
    const image = category.category_image || '';
    if (/^https?:\/\//i.test(image) || image.startsWith('/')) return image;
    if (/^[0-9a-f-]{36}\.(png|jpe?g)$/i.test(image)) return `${environment.apiBase}/api/product-images/${image}`;
    return this.defaultImages[fallbackKey] || '/images/LogoWall.png';
  }
}
