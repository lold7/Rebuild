import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../core/utils/api.utils';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatIconModule],
  templateUrl: './forgot-password.component.html'
})
export class ForgotPasswordComponent {
  private authService = inject(AuthService);

  email = '';
  isLoading = false;
  errorMessage = '';
  successMessage = '';
  /** Demo mode: the backend returns the link instead of emailing it. */
  resetLink = '';
  sentTo = '';

  onSubmit(): void {
    if (!this.email.trim()) return;
    this.isLoading = true;
    this.errorMessage = '';
    this.authService.forgotPassword(this.email.trim()).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.sentTo = this.email.trim();
        this.successMessage = res.message || 'If an account exists for that email, a reset link has been sent.';
        this.resetLink = res.resetLink ? this.toAppLink(res.resetLink) : '';
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = apiErrorMessage(err, 'Unable to send a reset link right now.');
      }
    });
  }

  /** Open the link inside this app when it points at our reset route. */
  private toAppLink(link: string): string {
    const match = link.match(/\/auth\/reset-password\/([^/?#]+)/) || link.match(/\/reset-password\/([^/?#]+)/);
    return match ? `/auth/reset-password/${match[1]}` : link;
  }

  isInternal(link: string): boolean {
    return link.startsWith('/');
  }
}
