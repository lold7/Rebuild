import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../core/utils/api.utils';

/** Lets a signed-in admin create another admin account (feature #5). */
@Component({
  selector: 'app-admin-register',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, MatIconModule],
  templateUrl: './admin-register.component.html',
  styleUrls: ['../admin-login/admin-login.component.scss']
})
export class AdminRegisterComponent {
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  form: FormGroup = this.fb.group({
    username: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  });

  errorMessage = '';
  successMessage = '';
  isLoading = false;

  onSubmit(): void {
    this.errorMessage = '';
    this.successMessage = '';
    const v = this.form.value;
    if (this.form.invalid) {
      this.errorMessage = this.form.get('password')?.hasError('minlength')
        ? 'Password must be at least 8 characters.'
        : 'Please fill in username, email and password.';
      return;
    }
    if (v.password !== v.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }
    this.isLoading = true;
    this.authService.createAdmin(v).subscribe({
      next: (admin) => {
        this.isLoading = false;
        this.successMessage = `Admin account "${admin.username}" (${admin.email}) was created.`;
        this.form.reset();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.status === 409 ? 'This email is already registered.' : apiErrorMessage(err, 'Failed to create admin account.');
      }
    });
  }
}
