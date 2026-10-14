import { MatIconModule } from '@angular/material/icon';
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/** Returns & refunds, privacy, terms of service and cookie settings. Footer links jump to each section. */
@Component({
  selector: 'app-condition',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule],
  templateUrl: './condition.component.html',
  styleUrls: ['./condition.component.scss']
})
export class ConditionComponent {
  authService = inject(AuthService);

  policies = [
    {
      id: 'returns', label: 'Returns', title: 'Returns & refunds', action: 'Request a return', link: '/contact',
      points: [
        'Return any book within 30 days of purchase, as long as it is in its original condition.',
        'Opened or damaged books may be eligible for an exchange instead of a refund.',
        'Return shipping is free on qualifying items.',
        'Once we receive your return, refunds go back to your original payment method within five business days.'
      ]
    },
    {
      id: 'privacy', label: 'Privacy', title: 'Privacy policy', action: 'Manage account', link: '/account/profile',
      points: [
        'We store your name, email, phone number, and delivery addresses only to run your account and deliver your orders.',
        'Passwords are stored as encrypted hashes and are never visible to our staff.',
        'We never sell or share your personal information with third parties for marketing.',
        'You can update your details anytime from your Account page.'
      ]
    },
    {
      id: 'terms', label: 'Terms', title: 'Terms of service', action: 'View order history', link: '/account/orders',
      points: [
        'You need an account to place an order, and you are responsible for keeping your login details safe.',
        'Prices and stock are shown at the time you order; an order is confirmed once it has been placed successfully.',
        'We may cancel an order if an item becomes unavailable, and any payment will be refunded in full.',
        'Order status can be followed in your Order History.'
      ]
    },
    {
      id: 'cookies', label: 'Cookies', title: 'Cookie settings', action: 'Read the FAQ', link: '/faq',
      points: [
        'We use a session cookie to keep you signed in, and a Remember-me cookie only if you choose it.',
        'We do not use advertising or third-party tracking cookies.',
        'Blocking cookies in your browser will stop login and the cart from working.'
      ]
    }
  ];
}
