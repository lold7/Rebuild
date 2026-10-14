import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../core/utils/api.utils';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './reset-password.component.html'
})
export class ResetPasswordComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);

  token = '';
  state: 'checking' | 'form' | 'invalid' | 'done' = 'checking';
  newPassword = '';
  confirmPassword = '';
  errorMessage = '';
  successMessage = '';
  isLoading = false;

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    this.authService.checkResetToken(this.token).subscribe(valid => {
      this.state = valid ? 'form' : 'invalid';
    });
  }

  onSubmit(): void {
    this.errorMessage = '';
    if (this.newPassword.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters.';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }
    this.isLoading = true;
    this.authService.resetPassword(this.token, this.newPassword, this.confirmPassword).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.successMessage = res.message || 'Password reset successfully. Please log in.';
        this.state = 'done';
      },
      error: (err) => {
        this.isLoading = false;
        if (err.error?.code === 'INVALID_RESET_TOKEN') {
          this.state = 'invalid';
        } else {
          this.errorMessage = apiErrorMessage(err, 'Unable to reset your password.');
        }
      }
    });
  }
}
