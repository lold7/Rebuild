import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ReactiveFormsModule, MatSnackBarModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

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
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required]
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
        this.successMessage = 'Profile updated successfully!';
        this.snackBar.open('Profile updated successfully!', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to update profile';
        this.snackBar.open(this.errorMessage, 'Close', { duration: 3000 });
      }
    });
  }

  onChangePassword(): void {
    if (this.passwordForm.invalid) return;
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.value;

    if (newPassword !== confirmPassword) {
      this.errorMessage = 'New passwords do not match';
      this.snackBar.open(this.errorMessage, 'Close', { duration: 3000 });
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.authService.changePassword({ currentPassword, newPassword, confirmPassword }).subscribe({
      next: () => {
        this.successMessage = 'Password changed successfully!';
        this.passwordForm.reset();
        this.snackBar.open('Password changed successfully!', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to change password';
        this.snackBar.open(this.errorMessage, 'Close', { duration: 3000 });
      }
    });
  }
}
