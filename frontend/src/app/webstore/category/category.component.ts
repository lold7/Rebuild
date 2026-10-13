import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ProductService } from '../../core/services/product.service';
import { CartService } from '../../core/services/cart.service';
import { Category, Product } from '../../core/models/models';

interface CategoryConfig {
  title: string;
  subtitle: string;
  highlightBadge: string;
  highlightTitle: string;
  highlightDesc: string;
  highlightImage: string;
  highlightProductId?: number;
  featuredPicks: { label: string; title: string; image: string }[];
}

@Component({
  selector: 'app-category',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule],
  templateUrl: './category.component.html',
  styleUrls: ['./category.component.scss']
})
export class CategoryComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private productService = inject(ProductService);
  private cartService = inject(CartService);

  slug = '';
  category: Category | null = null;
  products: Product[] = [];
  config!: CategoryConfig;

  categoryConfigs: { [key: string]: CategoryConfig } = {
    fiction: {
      title: 'Fiction and stories',
      subtitle: 'Explore our curated collection of novels, short stories, and timeless tales.',
      highlightBadge: 'Historical Fiction',
      highlightTitle: 'Europe War',
      highlightDesc: 'A novel of loss and redemption in post-war Europe. Discover the gripping tale that everyone is talking about.',
      highlightImage: 'https://m.media-amazon.com/images/I/61qxa9zrFOL._AC_UF894,1000_QL80_.jpg',
      highlightProductId: 1,
      featuredPicks: [
        { label: 'Featured', title: 'Award-winning titles', image: 'https://m.media-amazon.com/images/I/61qxa9zrFOL._AC_UF894,1000_QL80_.jpg' },
        { label: 'Curated', title: 'Staff picks', image: 'https://m.media-amazon.com/images/I/61qxa9zrFOL._AC_UF894,1000_QL80_.jpg' },
        { label: 'Readers', title: 'Top rated by readers', image: 'https://m.media-amazon.com/images/I/61qxa9zrFOL._AC_UF894,1000_QL80_.jpg' }
      ]
    },
    children: {
      title: "Children's books & stories",
      subtitle: 'Inspire young minds with enchanting stories and colorful picture books.',
      highlightBadge: "Children's Choice",
      highlightTitle: "Charlotte's Web",
      highlightDesc: 'The heartwarming story of a pig named Wilbur and his friendship with a spider named Charlotte.',
      highlightImage: 'https://bci.kinokuniya.com/jsp/images/book-img/97861/97861659/9786165929271.JPG',
      highlightProductId: 13,
      featuredPicks: [
        { label: 'Ages 3-5', title: 'Picture books', image: 'https://bci.kinokuniya.com/jsp/images/book-img/97861/97861659/9786165929271.JPG' },
        { label: 'Ages 6-8', title: 'Early readers', image: 'https://bci.kinokuniya.com/jsp/images/book-img/97861/97861659/9786165929271.JPG' },
        { label: 'Ages 9-12', title: 'Middle grade', image: 'https://bci.kinokuniya.com/jsp/images/book-img/97861/97861659/9786165929271.JPG' }
      ]
    },
    nonfiction: {
      title: 'Non-fiction and ideas',
      subtitle: 'Real stories, transformative knowledge, and inspiring memoirs.',
      highlightBadge: 'Bestseller',
      highlightTitle: 'Sapiens: A Brief History',
      highlightDesc: 'An exploration of how Homo sapiens came to dominate the earth through revolutions.',
      highlightImage: 'https://images.unsplash.com/photo-1532012197267-da84d127e765?q=80&w=600&auto=format&fit=crop',
      highlightProductId: 25,
      featuredPicks: [
        { label: 'Biography', title: 'Memoirs & biographies', image: 'https://images.unsplash.com/photo-1532012197267-da84d127e765?q=80&w=600&auto=format&fit=crop' },
        { label: 'Self Help', title: 'Productivity & habits', image: 'https://images.unsplash.com/photo-1532012197267-da84d127e765?q=80&w=600&auto=format&fit=crop' },
        { label: 'History', title: 'World history', image: 'https://images.unsplash.com/photo-1532012197267-da84d127e765?q=80&w=600&auto=format&fit=crop' }
      ]
    },
    mystery: {
      title: 'Mystery & Thriller novels',
      subtitle: 'Unravel thrilling puzzles, dark secrets, and edge-of-your-seat suspense.',
      highlightBadge: 'Page Turner',
      highlightTitle: 'Gone Girl',
      highlightDesc: 'A husband becomes the prime suspect when his wife disappears on their fifth anniversary.',
      highlightImage: 'https://m.media-amazon.com/images/I/81De-qFwVmL._AC_UF1000,1000_QL80_.jpg',
      highlightProductId: 37,
      featuredPicks: [
        { label: 'Classic', title: 'Agatha Christie classics', image: 'https://m.media-amazon.com/images/I/81De-qFwVmL._AC_UF1000,1000_QL80_.jpg' },
        { label: 'Psychological', title: 'Mind-bending thrillers', image: 'https://m.media-amazon.com/images/I/81De-qFwVmL._AC_UF1000,1000_QL80_.jpg' },
        { label: 'Crime', title: 'Detective investigations', image: 'https://m.media-amazon.com/images/I/81De-qFwVmL._AC_UF1000,1000_QL80_.jpg' }
      ]
    },
    science: {
      title: 'Science & Technology',
      subtitle: 'Dive deep into universe mysteries, computing innovations, and futuristic tech.',
      highlightBadge: 'Essential Read',
      highlightTitle: 'A Brief History of Time',
      highlightDesc: 'From the Big Bang to black holes, Stephen Hawking explores the cosmos.',
      highlightImage: 'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop',
      highlightProductId: 49,
      featuredPicks: [
        { label: 'Astrophysics', title: 'Cosmic wonders', image: 'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop' },
        { label: 'Coding', title: 'Software craftsmanship', image: 'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop' },
        { label: 'Biotech', title: 'Genetics & life sciences', image: 'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop' }
      ]
    }
  };

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.slug = params.get('slug') || 'fiction';
      this.loadCategory();
    });
  }

  loadCategory(): void {
    this.config = this.categoryConfigs[this.slug.toLowerCase()] || this.categoryConfigs['fiction'];
    this.productService.getCategoryBySlug(this.slug).subscribe({
      next: (res) => {
        this.category = res.category;
        this.products = res.products;
      },
      error: () => {}
    });
  }

  goToProduct(id: number): void {
    this.router.navigate(['/product', id]);
  }

  addToCart(productId?: number): void {
    if (productId) {
      this.cartService.addToCart(productId, 1, { format: 'Paperback' }).subscribe({
        next: () => {},
        error: () => {
          this.router.navigate(['/auth/login']);
        }
      });
    }
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
