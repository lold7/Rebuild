import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { ProductService } from '../../core/services/product.service';
import { FAQ } from '../../core/models/models';

@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [CommonModule, RouterModule, MatExpansionModule],
  templateUrl: './faq.component.html',
  styleUrls: ['./faq.component.scss']
})
export class FaqComponent implements OnInit {
  private productService = inject(ProductService);

  faqs: FAQ[] = [];
  isLoading = true;

  ngOnInit(): void {
    this.productService.getFaqs().subscribe({
      next: (data) => {
        this.faqs = data || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }
}
