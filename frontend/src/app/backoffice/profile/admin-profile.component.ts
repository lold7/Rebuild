import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { apiErrorMessage } from '../../core/utils/api.utils';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-admin-profile',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, ReactiveFormsModule, AdminSidebarComponent, AdminTopbarComponent, MatSnackBarModule],
  templateUrl: './admin-profile.component.html',
  styleUrls: ['./admin-profile.component.scss']
})
export class AdminProfileComponent implements OnInit {
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);
  private router = inject(Router);

  currentUser: User | null = null;
  profileForm!: FormGroup;
  passwordForm!: FormGroup;

  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;

    this.profileForm = this.fb.group({
      username: [this.currentUser?.username || '', Validators.required],
      email: [this.currentUser?.email || '', [Validators.required, Validators.email]],
      phone: [this.currentUser?.phone || '']
    });

    this.passwordForm = this.fb.group({
      current_password: ['', Validators.required],
      new_password: ['', [Validators.required, Validators.minLength(8)]],
      confirm_password: ['', Validators.required]
    });

    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUser = user;
        this.profileForm.patchValue({
          username: user.username,
          email: user.email,
          phone: user.phone || ''
        });
      }
    });
  }

  onSaveProfile(): void {
    if (this.profileForm.invalid) return;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.updateProfile(this.profileForm.value).subscribe({
      next: (res) => {
        if (res.reauthenticationRequired) {
          this.goToLogin('Email updated. Please log in again with your new email.');
          return;
        }
        this.successMessage = 'Profile updated successfully!';
        this.snackBar.open('Profile updated successfully!', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.errorMessage = err.status === 409 ? 'That email is already in use.' : apiErrorMessage(err, 'Failed to update profile');
        this.snackBar.open(this.errorMessage, 'Close', { duration: 3000 });
      }
    });
  }

  onChangePassword(): void {
    if (this.passwordForm.invalid) return;
    const { current_password, new_password, confirm_password } = this.passwordForm.value;

    if (new_password !== confirm_password) {
      this.errorMessage = 'New passwords do not match';
      this.snackBar.open(this.errorMessage, 'Close', { duration: 3000 });
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.authService.changePassword({
      currentPassword: current_password,
      newPassword: new_password,
      confirmPassword: confirm_password
    }).subscribe({
      next: () => {
        // Changing the password ends every session, including this one.
        this.passwordForm.reset();
        this.goToLogin('Password changed successfully. Please log in with your new password.');
      },
      error: (err) => {
        this.errorMessage = err.error?.code === 'INCORRECT_CURRENT_PASSWORD'
          ? 'Current password is incorrect'
          : apiErrorMessage(err, 'Failed to change password');
        this.snackBar.open(this.errorMessage, 'Close', { duration: 3000 });
      }
    });
  }

  private goToLogin(message: string): void {
    this.snackBar.open(message, 'Close', { duration: 4000 });
    this.router.navigate(['/backoffice/login'], { queryParams: { message } });
  }
}
